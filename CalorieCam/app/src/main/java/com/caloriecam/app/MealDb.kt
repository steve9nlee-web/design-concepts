package com.caloriecam.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MealEntry(
    val id: Long = 0,
    val date: String,        // yyyy-MM-dd
    val time: String,        // HH:mm
    val foodName: String,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val notes: String,
    val synced: Boolean
)

class MealDb(context: Context) :
    SQLiteOpenHelper(context, "meals.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE meals (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                time TEXT NOT NULL,
                food_name TEXT NOT NULL,
                calories INTEGER NOT NULL,
                protein_g INTEGER NOT NULL,
                carbs_g INTEGER NOT NULL,
                fat_g INTEGER NOT NULL,
                notes TEXT NOT NULL DEFAULT '',
                synced INTEGER NOT NULL DEFAULT 0
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insert(entry: MealEntry): Long {
        val values = ContentValues().apply {
            put("date", entry.date)
            put("time", entry.time)
            put("food_name", entry.foodName)
            put("calories", entry.calories)
            put("protein_g", entry.proteinG)
            put("carbs_g", entry.carbsG)
            put("fat_g", entry.fatG)
            put("notes", entry.notes)
            put("synced", if (entry.synced) 1 else 0)
        }
        return writableDatabase.insert("meals", null, values)
    }

    fun markSynced(id: Long) {
        val values = ContentValues().apply { put("synced", 1) }
        writableDatabase.update("meals", values, "id = ?", arrayOf(id.toString()))
    }

    fun mealsForDate(date: String): List<MealEntry> = query("date = ?", arrayOf(date))

    fun allMeals(limit: Int = 200): List<MealEntry> = query(null, null, limit)

    fun unsyncedMeals(): List<MealEntry> = query("synced = 0", null)

    private fun query(where: String?, args: Array<String>?, limit: Int = 500): List<MealEntry> {
        val list = mutableListOf<MealEntry>()
        readableDatabase.query(
            "meals", null, where, args, null, null, "date DESC, time DESC", limit.toString()
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    MealEntry(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        date = c.getString(c.getColumnIndexOrThrow("date")),
                        time = c.getString(c.getColumnIndexOrThrow("time")),
                        foodName = c.getString(c.getColumnIndexOrThrow("food_name")),
                        calories = c.getInt(c.getColumnIndexOrThrow("calories")),
                        proteinG = c.getInt(c.getColumnIndexOrThrow("protein_g")),
                        carbsG = c.getInt(c.getColumnIndexOrThrow("carbs_g")),
                        fatG = c.getInt(c.getColumnIndexOrThrow("fat_g")),
                        notes = c.getString(c.getColumnIndexOrThrow("notes")),
                        synced = c.getInt(c.getColumnIndexOrThrow("synced")) == 1
                    )
                )
            }
        }
        return list
    }

    companion object {
        fun todayDate(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        fun nowTime(): String =
            SimpleDateFormat("HH:mm", Locale.US).format(Date())
    }
}
