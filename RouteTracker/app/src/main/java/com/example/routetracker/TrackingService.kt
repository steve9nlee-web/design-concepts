package com.example.routetracker

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.routetracker.db.TripStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground service that watches WiFi connectivity and records a GPS track
 * for every journey away from the designated "home" WiFi network.
 *
 * State machine:
 *  - AT_HOME:    connected to the home SSID, not recording.
 *  - RECORDING:  home SSID lost -> GPS points are logged until the home SSID
 *                is seen again.
 * Transitions are debounced so a brief WiFi hiccup at home does not create
 * a bogus trip and a momentary reconnect does not cut a real trip short.
 */
class TrackingService : Service(), LocationListener {

    companion object {
        const val ACTION_START = "com.example.routetracker.START"
        const val ACTION_STOP = "com.example.routetracker.STOP"
        const val ACTION_TRIPS_CHANGED = "com.example.routetracker.TRIPS_CHANGED"

        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1

        /** WiFi must be gone this long before a trip starts. */
        private const val START_DEBOUNCE_MS = 25_000L

        /** WiFi must be back this long before a trip ends. */
        private const val STOP_DEBOUNCE_MS = 10_000L

        private const val GPS_INTERVAL_MS = 5_000L
        private const val GPS_MIN_DISTANCE_M = 5f

        /** Trips shorter than this with no movement are discarded as WiFi flukes. */
        private const val MIN_TRIP_DURATION_MS = 60_000L

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(
                ctx, Intent(ctx, TrackingService::class.java).setAction(ACTION_START)
            )
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, TrackingService::class.java).setAction(ACTION_STOP))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var locationManager: LocationManager
    private lateinit var store: TripStore

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var onHomeWifi = false
    private var recordingTripId = -1L
    private var pendingStart: Runnable? = null
    private var pendingStop: Runnable? = null
    private var pointsThisTrip = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        store = TripStore.get(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notif_waiting)))
        Prefs.setMonitoringEnabled(this, true)

        // Resume a trip that was in flight when the process died.
        val activeId = Prefs.getActiveTripId(this)
        if (activeId > 0 && store.getTrip(activeId)?.endTime == null) {
            recordingTripId = activeId
            startLocationUpdates()
            updateNotification(getString(R.string.notif_recording))
        }

        registerWifiCallback()
        evaluateInitialState()
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterWifiCallback()
        stopLocationUpdates()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun shutdown() {
        Prefs.setMonitoringEnabled(this, false)
        // Close any open trip so it is not left dangling.
        if (recordingTripId > 0) finishTrip()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------------------------------------------------------------- WiFi --

    private fun registerWifiCallback() {
        if (networkCallback != null) return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val callback = if (Build.VERSION.SDK_INT >= 31) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    handleWifiInfo((caps.transportInfo as? WifiInfo)?.ssid)
                }

                override fun onLost(network: Network) = handleWifiInfo(null)
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    handleWifiInfo(WifiUtils.currentSsid(this@TrackingService))
                }

                override fun onLost(network: Network) = handleWifiInfo(null)
            }
        }
        networkCallback = callback
        connectivityManager.registerNetworkCallback(request, callback)
    }

    private fun unregisterWifiCallback() {
        networkCallback?.let { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
        networkCallback = null
    }

    private fun evaluateInitialState() {
        handleWifiInfo(WifiUtils.currentSsid(this))
    }

    private fun handleWifiInfo(rawSsid: String?) {
        val home = Prefs.getHomeSsid(this) ?: return
        val ssid = WifiUtils.cleanSsid(rawSsid)
        val nowAtHome = ssid == home
        if (nowAtHome == onHomeWifi && (pendingStart == null && pendingStop == null)) return
        onHomeWifi = nowAtHome

        if (nowAtHome) {
            // Back in range: cancel a pending trip start, schedule trip end.
            pendingStart?.let { handler.removeCallbacks(it); pendingStart = null }
            if (recordingTripId > 0 && pendingStop == null) {
                val r = Runnable {
                    pendingStop = null
                    if (onHomeWifi && recordingTripId > 0) finishTrip()
                }
                pendingStop = r
                handler.postDelayed(r, STOP_DEBOUNCE_MS)
            }
        } else {
            // Out of range: cancel a pending trip end, schedule trip start.
            pendingStop?.let { handler.removeCallbacks(it); pendingStop = null }
            if (recordingTripId <= 0 && pendingStart == null) {
                val r = Runnable {
                    pendingStart = null
                    if (!onHomeWifi && recordingTripId <= 0) beginTrip(home)
                }
                pendingStart = r
                handler.postDelayed(r, START_DEBOUNCE_MS)
            }
        }
    }

    // ---------------------------------------------------------------- Trip --

    private fun beginTrip(homeSsid: String) {
        // The trip starts when WiFi was actually lost, not when the debounce fired.
        val startTime = System.currentTimeMillis() - START_DEBOUNCE_MS
        recordingTripId = store.startTrip(startTime, homeSsid)
        Prefs.setActiveTripId(this, recordingTripId)
        pointsThisTrip = 0
        startLocationUpdates()
        updateNotification(getString(R.string.notif_recording))
        broadcastChange()
    }

    private fun finishTrip() {
        val tripId = recordingTripId
        recordingTripId = -1L
        Prefs.setActiveTripId(this, -1L)
        stopLocationUpdates()

        val trip = store.getTrip(tripId)
        if (trip != null) {
            val now = System.currentTimeMillis()
            val tooShort = now - trip.startTime < MIN_TRIP_DURATION_MS
            if (tooShort && store.pointCount(tripId) < 2) {
                store.deleteTrip(tripId) // WiFi blip, not a journey
            } else {
                store.endTrip(tripId, now)
            }
        }
        updateNotification(getString(R.string.notif_waiting))
        broadcastChange()
    }

    // ------------------------------------------------------------ Location --

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, GPS_INTERVAL_MS, GPS_MIN_DISTANCE_M, this
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, GPS_INTERVAL_MS * 3, GPS_MIN_DISTANCE_M, this
                )
            }
        } catch (_: SecurityException) {
        }
    }

    private fun stopLocationUpdates() {
        runCatching { locationManager.removeUpdates(this) }
    }

    override fun onLocationChanged(location: Location) {
        val tripId = recordingTripId
        if (tripId <= 0) return
        if (location.hasAccuracy() && location.accuracy > 75f) return // ignore junk fixes
        store.addPoint(
            TrackPoint(
                tripId = tripId,
                time = location.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
                lat = location.latitude,
                lon = location.longitude,
                accuracy = if (location.hasAccuracy()) location.accuracy else -1f,
                speed = if (location.hasSpeed()) location.speed else 0f
            )
        )
        pointsThisTrip++
        if (pointsThisTrip % 12 == 1) { // refresh notification occasionally
            val since = SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(Date(store.getTrip(tripId)?.startTime ?: 0L))
            updateNotification(getString(R.string.notif_recording_since, since, pointsThisTrip))
        }
    }

    @Deprecated("Deprecated in API 29, still required for older devices")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    // -------------------------------------------------------- Notification --

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun broadcastChange() {
        sendBroadcast(Intent(ACTION_TRIPS_CHANGED).setPackage(packageName))
    }
}
