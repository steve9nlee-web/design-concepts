package com.caloriecam.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Posts meal rows to a Google Apps Script Web App, which appends them to a
 * Google Sheet in the user's Drive (see docs/google-sheets-apps-script.gs).
 */
object SheetSync {

    data class SyncResult(val ok: Boolean, val message: String)

    /**
     * Pushes one meal row and returns a detailed result. The Apps Script must
     * answer with the JSON {"status":"ok"} — anything else (a Google sign-in
     * page, an error page, a wrong URL) is reported as a failure with a reason,
     * never silently treated as success.
     */
    fun pushRow(sheetUrl: String, profile: UserProfile, meal: MealEntry): SyncResult {
        if (sheetUrl.isBlank()) {
            return SyncResult(false, "No Sheet URL set. Add it in Settings.")
        }
        if (!sheetUrl.startsWith("https://script.google.com/")) {
            return SyncResult(
                false,
                "The URL doesn't look like an Apps Script Web App URL. " +
                "It must start with https://script.google.com/macros/… and end with /exec."
            )
        }
        if (!sheetUrl.trimEnd('/').endsWith("/exec")) {
            return SyncResult(
                false,
                "The URL must be the Web App URL ending in /exec " +
                "(not the /dev, library, or editor URL)."
            )
        }

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
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

                // Apps Script answers POSTs with a 302 to a one-time result URL on
                // script.googleusercontent.com; follow redirects manually because
                // HttpURLConnection won't cross a host boundary on its own.
                var code = conn.responseCode
                var hops = 0
                while (code in 301..303 && hops < 4) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location.isNullOrBlank()) {
                        return SyncResult(false, "Endpoint redirected without a target (HTTP $code).")
                    }
                    if (location.contains("accounts.google.com")) {
                        return SyncResult(
                            false,
                            "Google is asking for sign-in. In Apps Script, redeploy the Web App " +
                            "with \"Who has access\" set to Anyone."
                        )
                    }
                    conn = URL(location).openConnection() as HttpURLConnection
                    conn.connectTimeout = 20_000
                    conn.readTimeout = 30_000
                    code = conn.responseCode
                    hops++
                }

                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.readText()?.take(4000) ?: ""

                interpret(code, body)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            SyncResult(false, "Network error: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun interpret(code: Int, body: String): SyncResult {
        // Success is ONLY the script's own JSON answer.
        val jsonStart = body.indexOf('{')
        if (jsonStart >= 0) {
            try {
                val json = JSONObject(body.substring(jsonStart))
                if (json.optString("status") == "ok" ||
                    json.optString("status").startsWith("CalorieCam")
                ) {
                    return SyncResult(true, "Row added to your Google Sheet ✔")
                }
                val err = json.optString("error", json.optString("message"))
                if (err.isNotBlank()) return SyncResult(false, "Script error: $err")
            } catch (_: Exception) {
                // fall through to the checks below
            }
        }

        val lower = body.lowercase()
        return when {
            code == 404 ->
                SyncResult(false, "Endpoint not found (HTTP 404). Re-copy the Web App URL from " +
                    "Apps Script → Deploy → Manage deployments.")
            code == 401 || code == 403 || lower.contains("sign in") || lower.contains("accounts.google.com") ->
                SyncResult(false, "Access denied. Redeploy the Web App with \"Who has access\" = Anyone " +
                    "and \"Execute as\" = Me, then use the NEW /exec URL.")
            lower.contains("script function not found") || lower.contains("dopost") ->
                SyncResult(false, "The script has no doPost function. Paste the full bridge script, " +
                    "save, and deploy a NEW version.")
            lower.contains("authorization") || lower.contains("permission") ->
                SyncResult(false, "The script isn't authorized yet. In Apps Script, run doGet once " +
                    "and approve the permissions, then redeploy.")
            code in 200..299 ->
                SyncResult(false, "The endpoint answered with a web page instead of the script result. " +
                    "Usually this means access isn't set to \"Anyone\", or an old deployment " +
                    "version is live — deploy a New version and use its /exec URL.")
            else ->
                SyncResult(false, "HTTP $code from the endpoint. Check the deployment in Apps Script.")
        }
    }

    /** Sends a harmless test row so the user can verify the connection from Settings. */
    fun testConnection(profile: UserProfile): SyncResult {
        val testMeal = MealEntry(
            date = MealDb.todayDate(),
            time = MealDb.nowTime(),
            foodName = "Connection test",
            calories = 0,
            proteinG = 0, carbsG = 0, fatG = 0,
            notes = "Test row from CalorieCam — you can delete this row",
            synced = false
        )
        return pushRow(profile.sheetUrl, profile, testMeal)
    }

    /** Retry any locally-saved meals that never made it to the sheet. */
    fun syncPending(db: MealDb, profile: UserProfile): Int {
        val url = profile.sheetUrl
        if (url.isBlank()) return 0
        var synced = 0
        for (meal in db.unsyncedMeals()) {
            if (pushRow(url, profile, meal).ok) {
                db.markSynced(meal.id)
                synced++
            } else {
                break // same endpoint, same error — don't hammer it for every row
            }
        }
        return synced
    }
}
