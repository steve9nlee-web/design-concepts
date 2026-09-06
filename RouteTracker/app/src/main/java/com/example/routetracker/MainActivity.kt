package com.example.routetracker

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.routetracker.databinding.ActivityMainBinding
import com.example.routetracker.db.TripStore
import com.example.routetracker.ui.TripAdapter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: TripAdapter

    private val tripsChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshTrips()
    }

    private val basePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                requestBackgroundLocation()
            } else {
                binding.switchMonitoring.isChecked = false
                toast(getString(R.string.err_need_location))
            }
        }

    private val backgroundLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Even if "all the time" was denied we can still track while the
            // notification keeps the service alive; proceed either way.
            armMonitoring()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = TripAdapter(
            onClick = { trip ->
                startActivity(
                    Intent(this, TripDetailActivity::class.java)
                        .putExtra(TripDetailActivity.EXTRA_TRIP_ID, trip.id)
                )
            },
            onLongClick = { trip -> confirmDelete(trip.id) }
        )
        binding.tripList.layoutManager = LinearLayoutManager(this)
        binding.tripList.adapter = adapter

        binding.btnSetWifi.setOnClickListener { captureCurrentWifi() }

        binding.switchMonitoring.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (Prefs.getHomeSsid(this) == null) {
                    binding.switchMonitoring.isChecked = false
                    toast(getString(R.string.err_set_wifi_first))
                } else {
                    ensurePermissionsThenArm()
                }
            } else {
                TrackingService.stop(this)
                updateStatus()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            this, tripsChangedReceiver,
            IntentFilter(TrackingService.ACTION_TRIPS_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        binding.switchMonitoring.setOnCheckedChangeListener(null)
        binding.switchMonitoring.isChecked = Prefs.isMonitoringEnabled(this)
        binding.switchMonitoring.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (Prefs.getHomeSsid(this) == null) {
                    binding.switchMonitoring.isChecked = false
                    toast(getString(R.string.err_set_wifi_first))
                } else {
                    ensurePermissionsThenArm()
                }
            } else {
                TrackingService.stop(this)
                updateStatus()
            }
        }
        updateStatus()
        refreshTrips()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(tripsChangedReceiver)
    }

    // --------------------------------------------------------- Permissions --

    private fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensurePermissionsThenArm() {
        if (hasFineLocation()) {
            requestBackgroundLocation()
            return
        }
        val wanted = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        basePermissionLauncher.launch(wanted.toTypedArray())
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            AlertDialog.Builder(this)
                .setTitle(R.string.bg_location_title)
                .setMessage(R.string.bg_location_msg)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> armMonitoring() }
                .show()
        } else {
            armMonitoring()
        }
    }

    private fun armMonitoring() {
        TrackingService.start(this)
        binding.switchMonitoring.isChecked = true
        updateStatus()
    }

    // -------------------------------------------------------------- Wifi ----

    private fun captureCurrentWifi() {
        if (!hasFineLocation()) {
            basePermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            toast(getString(R.string.err_need_location))
            return
        }
        val ssid = WifiUtils.currentSsid(this)
        if (ssid == null) {
            toast(getString(R.string.err_no_wifi))
        } else {
            Prefs.setHomeSsid(this, ssid)
            updateStatus()
            toast(getString(R.string.wifi_saved, ssid))
        }
    }

    // ---------------------------------------------------------------- UI ----

    private fun updateStatus() {
        val ssid = Prefs.getHomeSsid(this)
        binding.txtHomeWifi.text = ssid?.let { getString(R.string.home_wifi_is, it) }
            ?: getString(R.string.home_wifi_unset)
        val monitoring = Prefs.isMonitoringEnabled(this)
        val recording = Prefs.getActiveTripId(this) > 0
        binding.txtStatus.text = when {
            recording -> getString(R.string.status_recording)
            monitoring -> getString(R.string.status_waiting)
            else -> getString(R.string.status_off)
        }
    }

    private fun refreshTrips() {
        val trips = TripStore.get(this).getTrips()
        adapter.submit(trips)
        binding.txtEmpty.visibility =
            if (trips.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun confirmDelete(tripId: Long) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_trip_title)
            .setMessage(R.string.delete_trip_msg)
            .setPositiveButton(R.string.delete) { _, _ ->
                TripStore.get(this).deleteTrip(tripId)
                refreshTrips()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toast(msg: String) =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
}
