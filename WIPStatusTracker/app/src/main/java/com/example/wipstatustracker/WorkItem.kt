package com.example.wipstatustracker

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class Status { URGENT, WIP, OK }

data class WorkItem(
    val id: String = UUID.randomUUID().toString(),
    val timestampMillis: Long = System.currentTimeMillis(),
    val company: String,
    val description: String,
    var status: Status = Status.WIP,
    var deadlineMillis: Long? = null
) {
    fun formattedTimestamp(): String =
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestampMillis))

    fun formattedDeadline(): String? = deadlineMillis?.let {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it))
    }

    /** True once the estimated finish date has passed (the whole due day still counts as on time). */
    fun isOverdue(now: Long = System.currentTimeMillis()): Boolean {
        val deadline = deadlineMillis ?: return false
        return now > endOfDay(deadline)
    }

    fun isDueToday(now: Long = System.currentTimeMillis()): Boolean {
        val deadline = deadlineMillis ?: return false
        return now <= endOfDay(deadline) && now >= startOfDay(deadline)
    }

    fun overdueDays(now: Long = System.currentTimeMillis()): Long {
        val deadline = deadlineMillis ?: return 0
        val past = now - endOfDay(deadline)
        if (past <= 0) return 0
        return TimeUnit.MILLISECONDS.toDays(past) + 1
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("timestamp", timestampMillis)
        put("company", company)
        put("description", description)
        put("status", status.name)
        deadlineMillis?.let { put("deadline", it) }
    }

    companion object {
        fun fromJson(json: JSONObject): WorkItem = WorkItem(
            id = json.getString("id"),
            timestampMillis = json.getLong("timestamp"),
            company = json.getString("company"),
            description = json.getString("description"),
            status = runCatching { Status.valueOf(json.getString("status")) }.getOrDefault(Status.WIP),
            deadlineMillis = if (json.has("deadline")) json.getLong("deadline") else null
        )

        private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        private fun endOfDay(millis: Long): Long = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

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
