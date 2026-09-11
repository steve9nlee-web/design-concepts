package com.mileageadmin.app

import android.content.Context
import android.content.SharedPreferences

/** Wraps the app's SharedPreferences: server URL and shared secret. */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("mileage_admin", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString("server_url", "") ?: ""
        set(value) = sp.edit().putString("server_url", value.trim()).apply()

    var secret: String
        get() = sp.getString("secret", "") ?: ""
        set(value) = sp.edit().putString("secret", value.trim()).apply()

    fun isConfigured(): Boolean = serverUrl.isNotBlank() && secret.isNotBlank()
}
