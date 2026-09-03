package com.perimeter.attendance.config

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Everything the trust rule depends on.
 *
 * In the shipped design these values are owned by the server and the phone only
 * reads them (HANDOFF.md section 6). There is no server yet, so they live here
 * and an admin edits them behind the PIN. That is the weak point of this build:
 * a rooted phone can rewrite these and change what the app *records*, not just
 * what it displays. When a backend exists, replace [load] with a fetch and drop
 * the setters.
 */
data class SiteConfig(
    val siteId: String,
    val siteName: String,
    val ssid: String,
    val allowedBssids: List<String>,
    val lat: Double,
    val lng: Double,
    val radiusM: Int,
    val graceSeconds: Int,
    val dwellSeconds: Int,
    val minAccuracyM: Int,
    val adminIdleSeconds: Int,
    val staffId: String,
    val staffName: String,
    val endpointUrl: String,
    val sharedSecret: String
) {
    /** BSSIDs are compared lower-case; vendors differ on casing. */
    fun bssidAllowed(bssid: String?): Boolean {
        if (bssid.isNullOrBlank()) return false
        val b = bssid.lowercase()
        // Android hands back this sentinel when the permission or the location
        // toggle is missing. Never treat it as a match — fail closed.
        if (b == "02:00:00:00:00:00") return false
        return allowedBssids.any { it.lowercase() == b }
    }

    companion object {
        private const val PREFS = "perimeter_site"

        /**
         * FILL THESE IN. Every value is overridable from the Rules screen once
         * you are past the admin PIN, but these are what a fresh install starts
         * with, so setting them here saves typing on each phone.
         */
        val DEFAULT = SiteConfig(
            siteId = "HQ-AMPANG",
            siteName = "HQ — Jalan Ampang",
            ssid = "CORP-STAFF",
            allowedBssids = listOf(
                "3c:07:54:aa:1d:02"   // <- your router's BSSID. Add one line per AP.
            ),
            lat = 3.15780,            // <- your site centre
            lng = 101.71170,
            radiusM = 100,
            graceSeconds = 60,
            dwellSeconds = 15,
            minAccuracyM = 30,
            adminIdleSeconds = 120,
            staffId = "EMP-0412",     // <- set per phone
            staffName = "Nadia Rahman",
            endpointUrl = "",         // <- your Apps Script /exec URL
            sharedSecret = ""         // <- must match SHARED_SECRET in AppendSession.gs
        )

        fun prefs(ctx: Context): SharedPreferences =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun load(ctx: Context): SiteConfig {
            val p = prefs(ctx)
            val d = DEFAULT
            return SiteConfig(
                siteId = p.getString("siteId", d.siteId) ?: d.siteId,
                siteName = p.getString("siteName", d.siteName) ?: d.siteName,
                ssid = p.getString("ssid", d.ssid) ?: d.ssid,
                allowedBssids = (p.getString("bssids", d.allowedBssids.joinToString(",")) ?: "")
                    .split(",").map { it.trim() }.filter { it.isNotEmpty() },
                lat = java.lang.Double.longBitsToDouble(
                    p.getLong("lat", java.lang.Double.doubleToRawLongBits(d.lat))
                ),
                lng = java.lang.Double.longBitsToDouble(
                    p.getLong("lng", java.lang.Double.doubleToRawLongBits(d.lng))
                ),
                radiusM = p.getInt("radiusM", d.radiusM),
                graceSeconds = p.getInt("graceSeconds", d.graceSeconds),
                dwellSeconds = p.getInt("dwellSeconds", d.dwellSeconds),
                minAccuracyM = p.getInt("minAccuracyM", d.minAccuracyM),
                adminIdleSeconds = p.getInt("adminIdleSeconds", d.adminIdleSeconds),
                staffId = p.getString("staffId", d.staffId) ?: d.staffId,
                staffName = p.getString("staffName", d.staffName) ?: d.staffName,
                endpointUrl = p.getString("endpointUrl", d.endpointUrl) ?: d.endpointUrl,
                sharedSecret = p.getString("sharedSecret", d.sharedSecret) ?: d.sharedSecret
            )
        }

        fun save(ctx: Context, c: SiteConfig) {
            prefs(ctx).edit()
                .putString("siteId", c.siteId)
                .putString("siteName", c.siteName)
                .putString("ssid", c.ssid)
                .putString("bssids", c.allowedBssids.joinToString(","))
                .putLong("lat", java.lang.Double.doubleToRawLongBits(c.lat))
                .putLong("lng", java.lang.Double.doubleToRawLongBits(c.lng))
                .putInt("radiusM", c.radiusM)
                .putInt("graceSeconds", c.graceSeconds)
                .putInt("dwellSeconds", c.dwellSeconds)
                .putInt("minAccuracyM", c.minAccuracyM)
                .putInt("adminIdleSeconds", c.adminIdleSeconds)
                .putString("staffId", c.staffId)
                .putString("staffName", c.staffName)
                .putString("endpointUrl", c.endpointUrl)
                .putString("sharedSecret", c.sharedSecret)
                .apply()
        }
    }
}

/**
 * The admin PIN.
 *
 * Stored only as a salted SHA-256 digest, so reading the prefs file does not
 * hand anyone the PIN. That is the floor, not the ceiling: a determined attacker
 * with the device can still brute-force four digits offline. The real fix is a
 * server-issued PIN and server-enforced settings — see HANDOFF.md section 2.
 */
object AdminPin {
    private const val PREFS = "perimeter_admin"
    private const val DEFAULT_PIN = "4917"
    const val MAX_TRIES = 5
    const val COOLDOWN_MS = 60_000L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun digest(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest((salt + pin).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun ensureSeeded(ctx: Context) {
        val p = prefs(ctx)
        if (p.getString("hash", null) != null) return
        set(ctx, DEFAULT_PIN)
    }

    fun set(ctx: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        prefs(ctx).edit()
            .putString("salt", salt)
            .putString("hash", digest(pin, salt))
            .putInt("len", pin.length)
            .putInt("tries", 0)
            .putLong("cooldownUntil", 0L)
            .apply()
    }

    fun pinLength(ctx: Context): Int {
        ensureSeeded(ctx)
        return prefs(ctx).getInt("len", 4)
    }

    /** Milliseconds still to wait, or 0 if the keypad is live. */
    fun cooldownRemainingMs(ctx: Context): Long {
        ensureSeeded(ctx)
        val until = prefs(ctx).getLong("cooldownUntil", 0L)
        val left = until - System.currentTimeMillis()
        return if (left > 0) left else 0
    }

    fun triesUsed(ctx: Context): Int {
        ensureSeeded(ctx)
        return prefs(ctx).getInt("tries", 0)
    }

    /**
     * The attempt counter is persisted, so force-quitting the app does not hand
     * an attacker a fresh five tries.
     */
    fun verify(ctx: Context, pin: String): Boolean {
        ensureSeeded(ctx)
        if (cooldownRemainingMs(ctx) > 0) return false
        val p = prefs(ctx)
        val salt = p.getString("salt", "") ?: ""
        val ok = digest(pin, salt) == p.getString("hash", null)
        if (ok) {
            p.edit().putInt("tries", 0).putLong("cooldownUntil", 0L).apply()
        } else {
            val tries = p.getInt("tries", 0) + 1
            val e = p.edit().putInt("tries", tries)
            if (tries >= MAX_TRIES) {
                e.putLong("cooldownUntil", System.currentTimeMillis() + COOLDOWN_MS)
                e.putInt("tries", 0)
            }
            e.apply()
        }
        return ok
    }
}
