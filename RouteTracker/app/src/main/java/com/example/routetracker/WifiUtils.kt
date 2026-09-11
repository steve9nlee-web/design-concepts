package com.example.routetracker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager

object WifiUtils {

    /** Strips the quotes Android wraps around SSIDs. Returns null for unknown/hidden SSIDs. */
    fun cleanSsid(raw: String?): String? {
        if (raw == null) return null
        val s = raw.trim().removeSurrounding("\"")
        return if (s.isEmpty() || s == WifiManager.UNKNOWN_SSID || s == "<unknown ssid>") null else s
    }

    /**
     * Current WiFi SSID, or null when not on WiFi or when the SSID is unavailable
     * (requires fine location permission and location services enabled).
     */
    @Suppress("DEPRECATION")
    fun currentSsid(ctx: Context): String? {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        // On S+ the WifiInfo may ride along in the transport info.
        val fromCaps = (caps.transportInfo as? WifiInfo)?.ssid
        cleanSsid(fromCaps)?.let { return it }
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return cleanSsid(wm.connectionInfo?.ssid)
    }
}
