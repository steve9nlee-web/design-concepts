package com.caloriecam.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Posts meal rows to a Google Apps Script Web App, which appends them to a
 * Google Sheet in the user's Drive (see docs/google-sheets-apps-script.gs).
 */
object SheetSync {

    /** Returns true when the row was accepted by the Apps Script endpoint. */
    fun pushRow(sheetUrl: String, profile: UserProfile, meal: MealEntry): Boolean {
        if (sheetUrl.isBlank()) return false
        val payload = JSONObject().apply {
            put("name", profile.name)
            put("height_cm", profile.heightCm)
            put("weight_kg", profile.weightKg)
            put("target_weight_kg", profile.targetWeightKg)
            put("date", meal.date)
            put("time", meal.time)
            put("food", meal.foodName)
            put("calories", meal.calories)
            put("protein_g", meal.proteinG)
            put("carbs_g", meal.carbsG)
            put("fat_g", meal.fatG)
            put("notes", meal.notes)
            put("daily_target_kcal", profile.dailyCalorieTarget())
        }
        return try {
            var conn = URL(sheetUrl).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 20_000
                conn.readTimeout = 30_000
                conn.doOutput = true
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                var code = conn.responseCode
                // Apps Script answers POSTs with a 302 to a result URL; follow it manually
                // because HttpURLConnection won't cross the host boundary automatically.
                if (code in 301..303) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location.isNullOrBlank()) return false
                    conn = URL(location).openConnection() as HttpURLConnection
                    conn.connectTimeout = 20_000
                    conn.readTimeout = 30_000
                    code = conn.responseCode
                }
                code in 200..299
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Retry any locally-saved meals that never made it to the sheet. */
    fun syncPending(db: MealDb, profile: UserProfile): Int {
        val url = profile.sheetUrl
        if (url.isBlank()) return 0
        var synced = 0
        for (meal in db.unsyncedMeals()) {
            if (pushRow(url, profile, meal)) {
                db.markSynced(meal.id)
                synced++
            }
        }
        return synced
    }
}
