package com.photobrander.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * Company profile + n8n webhook configuration, persisted on-device so every
 * generation request automatically carries the company logo and details.
 */
class AppSettings(private val context: Context) {

    private val prefs = context.getSharedPreferences("photo_brander", Context.MODE_PRIVATE)

    var webhookUrl: String
        get() = prefs.getString(KEY_WEBHOOK_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WEBHOOK_URL, value.trim()).apply()

    var companyName: String
        get() = prefs.getString(KEY_COMPANY_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_COMPANY_NAME, value.trim()).apply()

    var companyDetails: String
        get() = prefs.getString(KEY_COMPANY_DETAILS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_COMPANY_DETAILS, value.trim()).apply()

    val logoFile: File
        get() = File(context.filesDir, "company_logo.png")

    fun hasLogo(): Boolean = logoFile.exists() && logoFile.length() > 0

    fun loadLogo(): Bitmap? =
        if (hasLogo()) BitmapFactory.decodeFile(logoFile.absolutePath) else null

    /** Copies the picked image into private storage as the company logo. */
    fun saveLogo(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input) ?: return false
                logoFile.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun isConfigured(): Boolean = webhookUrl.isNotBlank()

    companion object {
        private const val KEY_WEBHOOK_URL = "webhook_url"
        private const val KEY_COMPANY_NAME = "company_name"
        private const val KEY_COMPANY_DETAILS = "company_details"
    }
}
