package com.fittrainer.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WorkoutRecord(
    val timestamp: Long,
    val area: String,
    val exercise: String,
    val ageCategory: String,
    val type: String,          // REPS or TIME
    val sets: Int,
    val amountPerSet: Int,     // reps or seconds
    val totalAmount: Int,
    val note: String
) {
    fun dateString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))

    fun timeString(): String =
        SimpleDateFormat("HH:mm", Locale.US).format(Date(timestamp))
}

object RecordStore {

    private const val FILE_NAME = "workout_records.json"

    private fun file(ctx: Context) = File(ctx.filesDir, FILE_NAME)

    fun load(ctx: Context): MutableList<WorkoutRecord> {
        val f = file(ctx)
        if (!f.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(f.readText())
            val out = mutableListOf<WorkoutRecord>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    WorkoutRecord(
                        o.getLong("ts"),
                        o.getString("area"),
                        o.getString("exercise"),
                        o.getString("age"),
                        o.optString("type", "REPS"),
                        o.getInt("sets"),
                        o.getInt("amount"),
                        o.getInt("total"),
                        o.optString("note", "")
                    )
                )
            }
            out
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun add(ctx: Context, r: WorkoutRecord) {
        val all = load(ctx)
        all.add(r)
        save(ctx, all)
    }

    fun clear(ctx: Context) = save(ctx, emptyList())

    private fun save(ctx: Context, records: List<WorkoutRecord>) {
        val arr = JSONArray()
        for (r in records) {
            arr.put(JSONObject().apply {
                put("ts", r.timestamp)
                put("area", r.area)
                put("exercise", r.exercise)
                put("age", r.ageCategory)
                put("type", r.type)
                put("sets", r.sets)
                put("amount", r.amountPerSet)
                put("total", r.totalAmount)
                put("note", r.note)
            })
        }
        file(ctx).writeText(arr.toString())
    }
}
