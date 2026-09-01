package com.mileageclaim.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Claim(
    val id: String = UUID.randomUUID().toString(),
    val date: String,          // yyyy-MM-dd
    val time: String,          // HH:mm
    val staff: String,
    val destination: String,
    val purpose: String,
    val km: Double,
    var sent: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("date", date)
        .put("time", time)
        .put("staff", staff)
        .put("destination", destination)
        .put("purpose", purpose)
        .put("km", km)
        .put("sent", sent)

    companion object {
        fun fromJson(o: JSONObject) = Claim(
            id = o.optString("id", UUID.randomUUID().toString()),
            date = o.optString("date"),
            time = o.optString("time"),
            staff = o.optString("staff"),
            destination = o.optString("destination"),
            purpose = o.optString("purpose"),
            km = o.optDouble("km", 0.0),
            sent = o.optBoolean("sent", false),
        )
    }
}

/** Claim history persisted as a JSON array in SharedPreferences, newest first. */
class ClaimStore(private val prefs: Prefs) {

    fun load(): MutableList<Claim> {
        val arr = JSONArray(prefs.claimsJson)
        val list = mutableListOf<Claim>()
        for (i in 0 until arr.length()) list.add(Claim.fromJson(arr.getJSONObject(i)))
        return list
    }

    fun save(claims: List<Claim>) {
        val arr = JSONArray()
        claims.forEach { arr.put(it.toJson()) }
        prefs.claimsJson = arr.toString()
    }

    fun add(claim: Claim) {
        val claims = load()
        claims.add(0, claim)
        // Cap the local history so the prefs entry can't grow without bound.
        while (claims.size > 200 && claims.last().sent) claims.removeAt(claims.size - 1)
        save(claims)
    }

    fun markSent(id: String) {
        val claims = load()
        claims.firstOrNull { it.id == id }?.sent = true
        save(claims)
    }

    fun pending(): List<Claim> = load().filter { !it.sent }
}
