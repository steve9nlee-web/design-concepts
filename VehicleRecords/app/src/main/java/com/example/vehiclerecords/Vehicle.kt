package com.example.vehiclerecords

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/** One renewable item (road tax, insurance or inspection): date as yyyy-MM-dd, cost as typed. */
data class Renewal(
    val date: String = "",
    val cost: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("date", date)
        put("cost", cost)
    }

    /**
     * Days from today until the renewal date. Negative means overdue,
     * null when no valid date is set.
     */
    fun daysLeft(): Long? {
        if (date.isBlank()) return null
        val parsed: Date = try {
            DATE_FORMAT.parse(date) ?: return null
        } catch (e: Exception) {
            return null
        }
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return TimeUnit.MILLISECONDS.toDays(parsed.time - today)
    }

    companion object {
        /** Renewals within this many days count as "due soon" (yellow). */
        const val DUE_SOON_DAYS = 60L

        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        fun fromJson(obj: JSONObject?): Renewal {
            if (obj == null) return Renewal()
            return Renewal(
                date = obj.optString("date", ""),
                cost = obj.optString("cost", "")
            )
        }
    }
}

data class Vehicle(
    val id: String = UUID.randomUUID().toString(),
    val plateNo: String = "",
    val make: String = "",
    val model: String = "",
    val color: String = "",
    val type: String = TYPE_CAR,
    val roadTax: Renewal = Renewal(),
    val insurance: Renewal = Renewal(),
    val inspection: Renewal = Renewal()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("plateNo", plateNo)
        put("make", make)
        put("model", model)
        put("color", color)
        put("type", type)
        put("roadTax", roadTax.toJson())
        put("insurance", insurance.toJson())
        put("inspection", inspection.toJson())
    }

    companion object {
        const val TYPE_CAR = "Car"
        const val TYPE_MOTORCYCLE = "Motorcycle"
        const val TYPE_VAN = "Van"

        fun fromJson(obj: JSONObject): Vehicle = Vehicle(
            id = obj.optString("id", UUID.randomUUID().toString()),
            plateNo = obj.optString("plateNo", ""),
            make = obj.optString("make", ""),
            model = obj.optString("model", ""),
            color = obj.optString("color", ""),
            type = obj.optString("type", TYPE_CAR),
            roadTax = Renewal.fromJson(obj.optJSONObject("roadTax")),
            insurance = Renewal.fromJson(obj.optJSONObject("insurance")),
            inspection = Renewal.fromJson(obj.optJSONObject("inspection"))
        )

        fun listToJson(vehicles: List<Vehicle>): String {
            val arr = JSONArray()
            vehicles.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(json: String): MutableList<Vehicle> {
            val result = mutableListOf<Vehicle>()
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    result.add(fromJson(arr.getJSONObject(i)))
                }
            } catch (e: Exception) {
                // Corrupt store: start empty rather than crash.
            }
            return result
        }
    }
}
