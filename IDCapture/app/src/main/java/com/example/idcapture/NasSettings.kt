package com.example.idcapture

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Connection settings for the UGREEN NAS (SMB), kept in encrypted shared
 * preferences because they include the NAS account password, plus the
 * enqueue helper for background uploads.
 */
object NasSettings {

    private const val PREFS = "nas_settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOST = "host"
    private const val KEY_SHARE = "share"
    private const val KEY_FOLDER = "folder"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"
    private const val DEFAULT_FOLDER = "IDCapture"

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)
    fun host(context: Context): String = prefs(context).getString(KEY_HOST, "").orEmpty()
    fun share(context: Context): String = prefs(context).getString(KEY_SHARE, "").orEmpty()
    fun folder(context: Context): String =
        prefs(context).getString(KEY_FOLDER, DEFAULT_FOLDER).orEmpty().ifBlank { DEFAULT_FOLDER }
    fun username(context: Context): String = prefs(context).getString(KEY_USERNAME, "").orEmpty()
    fun password(context: Context): String = prefs(context).getString(KEY_PASSWORD, "").orEmpty()

    fun save(
        context: Context,
        enabled: Boolean,
        host: String,
        share: String,
        folder: String,
        username: String,
        password: String,
    ) {
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putString(KEY_HOST, host.trim())
            .putString(KEY_SHARE, share.trim().trim('/'))
            .putString(KEY_FOLDER, folder.trim().trim('/'))
            .putString(KEY_USERNAME, username.trim())
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun isConfigured(context: Context): Boolean =
        host(context).isNotBlank() && share(context).isNotBlank()

    /** Queues one saved capture for upload; retries with backoff until it lands. */
    fun enqueueUpload(context: Context, uri: Uri, fileName: String) {
        val request = OneTimeWorkRequestBuilder<NasUploadWorker>()
            .setInputData(
                workDataOf(
                    NasUploadWorker.KEY_URI to uri.toString(),
                    NasUploadWorker.KEY_NAME to fileName,
                ),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("nas-upload")
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
