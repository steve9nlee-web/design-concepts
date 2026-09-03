package com.perimeter.attendance.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * One shift. [logoutAt] is null while it is still open.
 *
 * [rowKey] is the idempotency key from HANDOFF.md section 6 — staff_id|login_at.
 * A retry after a dropped connection updates the same row; it never appends a
 * duplicate.
 */
data class Session(
    val rowKey: String,
    val staffId: String,
    val staffName: String,
    val siteId: String,
    val loginAt: Long,
    val logoutAt: Long?,
    val ssid: String,
    val bssid: String,
    val bssidMatch: Boolean,
    val lat: Double,
    val lng: Double,
    val accuracyM: Int,
    val method: String,
    val deviceId: String,
    val flag: String,
    val synced: Boolean
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("row_key", rowKey)
        put("staff_id", staffId)
        put("name", staffName)
        put("site", siteId)
        put("login_at", iso(loginAt))
        put("logout_at", logoutAt?.let { iso(it) } ?: JSONObject.NULL)
        put("ssid", ssid)
        put("bssid", bssid)
        put("bssid_match", bssidMatch)
        put("lat", lat)
        put("lng", lng)
        put("accuracy_m", accuracyM)
        put("method", method)
        put("device_id", deviceId)
        put("flag", flag)
    }

    companion object {
        private fun fmt(pattern: String): SimpleDateFormat =
            SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

        fun iso(ms: Long): String = fmt("yyyy-MM-dd'T'HH:mm:ss'Z'").format(Date(ms))

        fun fromJson(o: JSONObject): Session = Session(
            rowKey = o.getString("rowKey"),
            staffId = o.getString("staffId"),
            staffName = o.optString("staffName"),
            siteId = o.optString("siteId"),
            loginAt = o.getLong("loginAt"),
            logoutAt = if (o.isNull("logoutAt")) null else o.getLong("logoutAt"),
            ssid = o.optString("ssid"),
            bssid = o.optString("bssid"),
            bssidMatch = o.optBoolean("bssidMatch"),
            lat = o.optDouble("lat", 0.0),
            lng = o.optDouble("lng", 0.0),
            accuracyM = o.optInt("accuracyM"),
            method = o.optString("method", "AUTO"),
            deviceId = o.optString("deviceId"),
            flag = o.optString("flag", "OK"),
            synced = o.optBoolean("synced", false)
        )
    }

    fun toStorageJson(): JSONObject = JSONObject().apply {
        put("rowKey", rowKey)
        put("staffId", staffId)
        put("staffName", staffName)
        put("siteId", siteId)
        put("loginAt", loginAt)
        put("logoutAt", logoutAt ?: JSONObject.NULL)
        put("ssid", ssid)
        put("bssid", bssid)
        put("bssidMatch", bssidMatch)
        put("lat", lat)
        put("lng", lng)
        put("accuracyM", accuracyM)
        put("method", method)
        put("deviceId", deviceId)
        put("flag", flag)
        put("synced", synced)
    }
}

/**
 * Sessions on disk, newest last. Deliberately a plain JSON file rather than Room:
 * no annotation processor, one less thing that can fail a first build. Swap for
 * Room if this ever grows beyond a few hundred rows.
 */
class SessionStore(private val ctx: Context) {

    private val file get() = java.io.File(ctx.filesDir, "sessions.json")

    @Synchronized
    fun all(): List<Session> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { Session.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    private fun writeAll(list: List<Session>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toStorageJson()) }
        file.writeText(arr.toString())
    }

    /** Insert, or replace the row with the same [Session.rowKey]. */
    @Synchronized
    fun upsert(s: Session) {
        val list = all().toMutableList()
        val i = list.indexOfFirst { it.rowKey == s.rowKey }
        if (i >= 0) list[i] = s else list.add(s)
        writeAll(list)
    }

    @Synchronized
    fun markSynced(rowKey: String) {
        val list = all().toMutableList()
        val i = list.indexOfFirst { it.rowKey == rowKey }
        if (i >= 0) {
            list[i] = list[i].copy(synced = true)
            writeAll(list)
        }
    }

    fun openSession(): Session? = all().lastOrNull { it.logoutAt == null }

    fun pending(): List<Session> = all().filter { !it.synced }

    fun recent(limit: Int = 20): List<Session> = all().takeLast(limit).reversed()
}
