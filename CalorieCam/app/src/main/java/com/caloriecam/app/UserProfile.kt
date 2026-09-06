package com.caloriecam.app

import android.content.Context
import android.content.SharedPreferences

/**
 * User profile and app settings, backed by SharedPreferences.
 */
class UserProfile(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("caloriecam", Context.MODE_PRIVATE)

    var name: String
        get() = prefs.getString("name", "") ?: ""
        set(v) = prefs.edit().putString("name", v).apply()

    var age: Int
        get() = prefs.getInt("age", 30)
        set(v) = prefs.edit().putInt("age", v).apply()

    /** "male" or "female" (used only for the BMR formula). */
    var sex: String
        get() = prefs.getString("sex", "male") ?: "male"
        set(v) = prefs.edit().putString("sex", v).apply()

    var heightCm: Float
        get() = prefs.getFloat("height_cm", 0f)
        set(v) = prefs.edit().putFloat("height_cm", v).apply()

    var weightKg: Float
        get() = prefs.getFloat("weight_kg", 0f)
        set(v) = prefs.edit().putFloat("weight_kg", v).apply()

    var targetWeightKg: Float
        get() = prefs.getFloat("target_weight_kg", 0f)
        set(v) = prefs.edit().putFloat("target_weight_kg", v).apply()

    /** 0 sedentary, 1 light, 2 moderate, 3 active */
    var activityLevel: Int
        get() = prefs.getInt("activity_level", 1)
        set(v) = prefs.edit().putInt("activity_level", v).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(v) = prefs.edit().putString("api_key", v.trim()).apply()

    var sheetUrl: String
        get() = prefs.getString("sheet_url", "") ?: ""
        set(v) = prefs.edit().putString("sheet_url", v.trim()).apply()

    val isComplete: Boolean
        get() = name.isNotBlank() && heightCm > 0 && weightKg > 0 && targetWeightKg > 0

    /** Mifflin-St Jeor basal metabolic rate. */
    fun bmr(): Double {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age
        return if (sex == "female") base - 161 else base + 5
    }

    /** Total daily energy expenditure. */
    fun tdee(): Double {
        val factor = when (activityLevel) {
            0 -> 1.2
            1 -> 1.375
            2 -> 1.55
            else -> 1.725
        }
        return bmr() * factor
    }

    /**
     * Daily calorie target: TDEE minus ~500 kcal when losing weight,
     * plus ~300 kcal when gaining, floor of 1200 kcal for safety.
     */
    fun dailyCalorieTarget(): Int {
        val tdee = tdee()
        val target = when {
            targetWeightKg < weightKg - 0.5 -> tdee - 500
            targetWeightKg > weightKg + 0.5 -> tdee + 300
            else -> tdee
        }
        return target.coerceAtLeast(1200.0).toInt()
    }
}
