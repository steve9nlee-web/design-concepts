package com.example.vehiclerecords

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: VehicleAdapter
    private lateinit var emptyView: TextView

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
