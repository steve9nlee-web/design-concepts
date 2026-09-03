package com.perimeter.attendance.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.perimeter.attendance.MainActivity
import com.perimeter.attendance.R
import com.perimeter.attendance.config.SiteConfig
import com.perimeter.attendance.data.Session
import com.perimeter.attendance.data.SessionStore
import com.perimeter.attendance.net.AppendSessionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The bit that actually keeps time.
 *
 * State machine, driven by location updates:
 *
 *   OUT ──all checks pass for dwellSeconds──▶ IN        (writes the open row)
 *   IN  ──checks fail for graceSeconds──────▶ OUT       (closes the row)
 *   IN  ──checks fail, then pass again──────▶ IN        (grace cancelled, nothing written)
 *
 * Dwell and grace are measured from timestamps, not countdown timers, so doze
 * or a missed callback cannot quietly shorten them.
 */
class AttendanceService : Service() {

    enum class Phase { OUT, PENDING_IN, IN, PENDING_OUT }

    data class UiState(
        val phase: Phase = Phase.OUT,
        val trust: TrustResult? = null,
        val sessionStart: Long? = null,
        val graceSecondsLeft: Int = 0,
        val dwellSecondsLeft: Int = 0,
        val lastError: String? = null,
        val locationAvailable: Boolean = true
    )

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STAY = "stay"          // "I'm still on site"
        const val ACTION_LOGOUT_NOW = "logout_now"
        const val ACTION_SYNC = "sync"

        private const val CHANNEL = "perimeter_status"
        private const val NOTIF_ID = 42

        private val _state = MutableStateFlow(UiState())
        val state = _state.asStateFlow()

        fun start(ctx: Context) {
            val i = Intent(ctx, AttendanceService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun send(ctx: Context, action: String) {
            val i = Intent(ctx, AttendanceService::class.java).setAction(action)
            ContextCompat.startForegroundService(ctx, i)
        }
    }

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var store: SessionStore
    private val scope = CoroutineScope(Dispatchers.IO)

    private var cfg: SiteConfig = SiteConfig.DEFAULT
    private var phase = Phase.OUT
    private var satisfiedSince: Long = 0L
    private var violatedSince: Long = 0L
    private var lastTrust: TrustResult? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { onLocation(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = SessionStore(this)
        cfg = SiteConfig.load(this)
        fused = LocationServices.getFusedLocationProviderClient(this)
        createChannel()

        // A session that was open when the process died is still open. Surface it
        // rather than silently dropping it — an unclosed row is a payroll question,
        // not something to hide.
        store.openSession()?.let {
            phase = Phase.IN
            _state.value = _state.value.copy(phase = Phase.IN, sessionStart = it.loginAt)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android 14 wants the type declared at the call site, not just in the
        // manifest, or it throws MissingForegroundServiceTypeException.
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        )
        cfg = SiteConfig.load(this)

        when (intent?.action) {
            ACTION_STAY -> {
                // Cancel a pending logout. Nothing is written.
                if (phase == Phase.PENDING_OUT) {
                    phase = Phase.IN
                    violatedSince = 0L
                    pushState()
                }
            }
            ACTION_LOGOUT_NOW -> closeSession(reason = "MANUAL")
            ACTION_SYNC -> scope.launch { drainQueue() }
        }

        requestLocation()
        scope.launch { drainQueue() }
        return START_STICKY
    }

    private fun requestLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = _state.value.copy(locationAvailable = false)
            return
        }
        val req = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30_000L)
            .setMinUpdateIntervalMillis(15_000L)
            .build()
        try {
            fused.requestLocationUpdates(req, callback, mainLooper)
            _state.value = _state.value.copy(locationAvailable = true)
        } catch (e: SecurityException) {
            _state.value = _state.value.copy(locationAvailable = false)
        }
    }

    private fun onLocation(loc: Location) {
        val trust = TrustCheck.evaluate(this, cfg, loc)
        lastTrust = trust
        val now = System.currentTimeMillis()

        if (trust.trusted) {
            violatedSince = 0L
            if (satisfiedSince == 0L) satisfiedSince = now
            when (phase) {
                Phase.OUT, Phase.PENDING_IN -> {
                    phase = Phase.PENDING_IN
                    if (now - satisfiedSince >= cfg.dwellSeconds * 1000L) {
                        openSession(trust, loc)
                    }
                }
                // Re-entry inside the grace window cancels the pending logout.
                Phase.PENDING_OUT -> phase = Phase.IN
                Phase.IN -> Unit
            }
        } else {
            satisfiedSince = 0L
            if (violatedSince == 0L) violatedSince = now
            when (phase) {
                Phase.IN, Phase.PENDING_OUT -> {
                    phase = Phase.PENDING_OUT
                    if (now - violatedSince >= cfg.graceSeconds * 1000L) {
                        closeSession(reason = "AUTO")
                    }
                }
                Phase.PENDING_IN -> phase = Phase.OUT
                Phase.OUT -> Unit
            }
        }
        pushState()
        updateNotification()
    }

    private fun openSession(trust: TrustResult, loc: Location) {
        val now = System.currentTimeMillis()
        val session = Session(
            rowKey = "${cfg.staffId}|${Session.iso(now)}",
            staffId = cfg.staffId,
            staffName = cfg.staffName,
            siteId = cfg.siteId,
            loginAt = now,
            logoutAt = null,
            ssid = trust.ssid,
            bssid = trust.bssid,
            bssidMatch = trust.bssidOk,
            lat = loc.latitude,
            lng = loc.longitude,
            accuracyM = trust.accuracyM,
            method = "AUTO",
            deviceId = deviceId(),
            flag = "OPEN",
            synced = false
        )
        store.upsert(session)
        phase = Phase.IN
        satisfiedSince = 0L
        // Two-phase write: the open row lands now, so HR sees the shift even if
        // the app dies before logout.
        scope.launch { drainQueue() }
    }

    private fun closeSession(reason: String) {
        val open = store.openSession() ?: run {
            phase = Phase.OUT
            pushState()
            return
        }
        val now = System.currentTimeMillis()
        val flag = when {
            lastTrust != null && lastTrust!!.accuracyM > cfg.minAccuracyM -> "LOW GPS"
            else -> "OK"
        }
        store.upsert(open.copy(logoutAt = now, flag = flag, method = reason, synced = false))
        phase = Phase.OUT
        violatedSince = 0L
        pushState()
        updateNotification()
        scope.launch { drainQueue() }
    }

    /** Push every unsynced row, oldest first. Order matters for the sheet. */
    private fun drainQueue() {
        val pending = store.pending()
        for (s in pending) {
            val r = AppendSessionClient.post(cfg.endpointUrl, cfg.sharedSecret, s)
            if (r.ok) {
                store.markSynced(s.rowKey)
            } else {
                // Stop on first failure — retrying the rest now would only
                // reorder them. The next location tick tries again.
                _state.value = _state.value.copy(lastError = r.message)
                return
            }
        }
        _state.value = _state.value.copy(lastError = null)
    }

    private fun pushState() {
        val now = System.currentTimeMillis()
        val graceLeft = if (phase == Phase.PENDING_OUT && violatedSince > 0)
            ((cfg.graceSeconds * 1000L - (now - violatedSince)) / 1000L).toInt().coerceAtLeast(0)
        else 0
        val dwellLeft = if (phase == Phase.PENDING_IN && satisfiedSince > 0)
            ((cfg.dwellSeconds * 1000L - (now - satisfiedSince)) / 1000L).toInt().coerceAtLeast(0)
        else 0

        _state.value = _state.value.copy(
            phase = phase,
            trust = lastTrust,
            sessionStart = store.openSession()?.loginAt,
            graceSecondsLeft = graceLeft,
            dwellSecondsLeft = dwellLeft
        )
    }

    private fun deviceId(): String =
        (Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown")
            .take(8).let { "${Build.MODEL.replace(' ', '-')}-$it" }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL, "Attendance", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows whether you are clocked in" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val open = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = when (phase) {
            Phase.IN -> "On site — clocked in"
            Phase.PENDING_IN -> "Verifying you're on site…"
            Phase.PENDING_OUT -> "Left the fence — logging out shortly"
            Phase.OUT -> "Not on site"
        }
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Perimeter")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_perimeter)
            .setOngoing(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // A silent logout generates payroll disputes. Give them the out.
        if (phase == Phase.PENDING_OUT) {
            val stay = PendingIntent.getService(
                this, 1,
                Intent(this, AttendanceService::class.java).setAction(ACTION_STAY),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            b.addAction(0, "I'm still on site", stay)
        }
        return b.build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification())
    }

    override fun onDestroy() {
        try {
            fused.removeLocationUpdates(callback)
        } catch (e: Exception) {
            // nothing useful to do; the process is going away
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
