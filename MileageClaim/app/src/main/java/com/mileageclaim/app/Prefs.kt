package com.mileageclaim.app

import android.content.Context
import android.content.SharedPreferences

/** Wraps the app's SharedPreferences: server settings, staff name, claim history. */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("mileage_claim", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString("server_url", "") ?: ""
        set(value) = sp.edit().putString("server_url", value.trim()).apply()

    var secret: String
        get() = sp.getString("secret", "") ?: ""
        set(value) = sp.edit().putString("secret", value.trim()).apply()

    var staffName: String
        get() = sp.getString("staff_name", "") ?: ""
        set(value) = sp.edit().putString("staff_name", value.trim()).apply()

    var claimsJson: String
        get() = sp.getString("claims", "[]") ?: "[]"
        set(value) = sp.edit().putString("claims", value).apply()

    fun isConfigured(): Boolean = serverUrl.isNotBlank() && secret.isNotBlank()
}
