package com.example.vehiclerecords

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: VehicleAdapter
    private lateinit var emptyView: TextView

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        emptyView = findViewById(R.id.emptyView)

        adapter = VehicleAdapter(
            onClick = { vehicle ->
                startActivity(
                    Intent(this, VehicleEditActivity::class.java)
                        .putExtra(VehicleEditActivity.EXTRA_VEHICLE_ID, vehicle.id)
                )
            },
            onLongClick = { vehicle -> confirmDelete(vehicle) }
        )

        val list = findViewById<RecyclerView>(R.id.vehicleList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<FloatingActionButton>(R.id.addFab).setOnClickListener {
            startActivity(Intent(this, VehicleEditActivity::class.java))
        }

        requestNotificationPermission()
        scheduleDailyRenewalCheck()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Checks renewals once a day around 9am and posts a reminder notification. */
    private fun scheduleDailyRenewalCheck() {
        val now = Calendar.getInstance()
        val nextNine = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        val request = PeriodicWorkRequestBuilder<RenewalCheckWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(nextNine.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            RenewalCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val vehicles = VehicleStore.load(this)
            .sortedBy { it.plateNo.lowercase() }
        adapter.submit(vehicles)
        emptyView.visibility = if (vehicles.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmDelete(vehicle: Vehicle) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_title, vehicle.plateNo))
            .setMessage(R.string.delete_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                VehicleStore.delete(this, vehicle.id)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
