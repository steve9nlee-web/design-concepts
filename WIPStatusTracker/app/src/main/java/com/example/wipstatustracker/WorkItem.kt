package com.example.wipstatustracker

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class Status { URGENT, WIP, OK }

data class WorkItem(
    val id: String = UUID.randomUUID().toString(),
    val timestampMillis: Long = System.currentTimeMillis(),
    val company: String,
    val description: String,
    var status: Status = Status.WIP
) {
    fun formattedTimestamp(): String =
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestampMillis))

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("timestamp", timestampMillis)
        put("company", company)
        put("description", description)
        put("status", status.name)
    }

    companion object {
        fun fromJson(json: JSONObject): WorkItem = WorkItem(
            id = json.getString("id"),
            timestampMillis = json.getLong("timestamp"),
            company = json.getString("company"),
            description = json.getString("description"),
            status = runCatching { Status.valueOf(json.getString("status")) }.getOrDefault(Status.WIP)
        )

        fun listToJson(items: List<WorkItem>): String {
            val array = JSONArray()
            items.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        fun listFromJson(raw: String): MutableList<WorkItem> {
            val array = JSONArray(raw)
            val items = mutableListOf<WorkItem>()
            for (i in 0 until array.length()) {
                items.add(fromJson(array.getJSONObject(i)))
            }
            return items
        }
    }
}
