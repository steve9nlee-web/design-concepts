package com.example.vehiclerecords

import android.content.Context

/** Simple on-device persistence backed by SharedPreferences (JSON). */
object VehicleStore {
    private const val PREFS = "vehicle_records"
    private const val KEY = "vehicles_json"

    fun load(context: Context): MutableList<Vehicle> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return mutableListOf()
        return Vehicle.listFromJson(json)
    }

    fun save(context: Context, vehicles: List<Vehicle>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, Vehicle.listToJson(vehicles))
            .apply()
    }

    fun upsert(context: Context, vehicle: Vehicle) {
        val vehicles = load(context)
        val index = vehicles.indexOfFirst { it.id == vehicle.id }
        if (index >= 0) vehicles[index] = vehicle else vehicles.add(vehicle)
        save(context, vehicles)
    }

    fun delete(context: Context, id: String) {
        val vehicles = load(context)
        vehicles.removeAll { it.id == id }
        save(context, vehicles)
    }

    fun find(context: Context, id: String): Vehicle? =
        load(context).firstOrNull { it.id == id }
}
