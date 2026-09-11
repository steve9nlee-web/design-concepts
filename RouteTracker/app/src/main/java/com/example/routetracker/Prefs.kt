package com.example.routetracker

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE = "routetracker_prefs"
    private const val KEY_HOME_SSID = "home_ssid"
    private const val KEY_MONITORING = "monitoring_enabled"
    private const val KEY_ACTIVE_TRIP = "active_trip_id"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getHomeSsid(ctx: Context): String? = sp(ctx).getString(KEY_HOME_SSID, null)

    fun setHomeSsid(ctx: Context, ssid: String?) =
        sp(ctx).edit().putString(KEY_HOME_SSID, ssid).apply()

    fun isMonitoringEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_MONITORING, false)

    fun setMonitoringEnabled(ctx: Context, enabled: Boolean) =
        sp(ctx).edit().putBoolean(KEY_MONITORING, enabled).apply()

    fun getActiveTripId(ctx: Context): Long = sp(ctx).getLong(KEY_ACTIVE_TRIP, -1L)

    fun setActiveTripId(ctx: Context, id: Long) =
        sp(ctx).edit().putLong(KEY_ACTIVE_TRIP, id).apply()
}
