package com.perimeter.attendance.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.perimeter.attendance.config.SiteConfig
import kotlin.math.roundToInt

/** What the phone can currently see of the network it is joined to. */
data class NetworkFacts(val ssid: String?, val bssid: String?)

/**
 * The three-part rule from HANDOFF.md section 1. All three must hold, and they
 * must hold *continuously* for the dwell period before a login is written.
 *
 * SSID alone is trusted for nothing: it is a string anyone can broadcast. The
 * BSSID pins the physical router, and the GPS fix defeats a look-alike hotspot
 * someone stands up down the road with the same name.
 */
data class TrustResult(
    val ssidOk: Boolean,
    val bssidOk: Boolean,
    val gpsOk: Boolean,
    val inFence: Boolean,
    val distanceM: Int,
    val accuracyM: Int,
    val ssid: String,
    val bssid: String
) {
    val trusted: Boolean get() = ssidOk && bssidOk && gpsOk && inFence

    /** Why the last check failed, in the words the Status screen shows. */
    val reason: String
        get() = when {
            trusted -> "All checks pass"
            !gpsOk -> "No usable GPS fix"
            !ssidOk -> "Not on the site network"
            !bssidOk -> "Router ID is not on the allow-list"
            !inFence -> "$distanceM m from site centre"
            else -> "Unverified"
        }
}

object TrustCheck {

    fun readNetwork(ctx: Context): NetworkFacts {
        // Android 13+ needs NEARBY_WIFI_DEVICES to see the BSSID at all; older
        // versions gate it behind location. Missing either -> fail closed.
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.NEARBY_WIFI_DEVICES
        else
            Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(ctx, needed) != PackageManager.PERMISSION_GRANTED) {
            return NetworkFacts(null, null)
        }
        return try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val info = wm.connectionInfo
            // getSSID() comes back quoted.
            val ssid = info?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
            NetworkFacts(ssid, info?.bssid)
        } catch (e: Exception) {
            NetworkFacts(null, null)
        }
    }

    fun evaluate(ctx: Context, cfg: SiteConfig, loc: Location?): TrustResult {
        val net = readNetwork(ctx)
        val ssidOk = net.ssid != null && net.ssid == cfg.ssid
        val bssidOk = cfg.bssidAllowed(net.bssid)

        val centre = Location("site").apply {
            latitude = cfg.lat
            longitude = cfg.lng
        }
        val distance = loc?.distanceTo(centre)?.roundToInt() ?: Int.MAX_VALUE
        val accuracy = loc?.accuracy?.roundToInt() ?: Int.MAX_VALUE
        val gpsOk = loc != null && accuracy <= cfg.minAccuracyM
        val inFence = loc != null && distance <= cfg.radiusM

        return TrustResult(
            ssidOk = ssidOk,
            bssidOk = bssidOk,
            gpsOk = gpsOk,
            inFence = inFence,
            distanceM = if (distance == Int.MAX_VALUE) -1 else distance,
            accuracyM = if (accuracy == Int.MAX_VALUE) -1 else accuracy,
            ssid = net.ssid ?: "—",
            bssid = net.bssid ?: "—"
        )
    }
}
