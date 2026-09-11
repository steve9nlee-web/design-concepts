package com.example.docscanner

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope
import java.util.concurrent.TimeUnit

/**
 * Settings and plumbing for the optional "copy captures to Google Drive" feature.
 *
 * Uses the narrow drive.file scope: the app can only see files and folders it
 * created itself, never the rest of the user's Drive.
 */
object DriveSync {

    const val DEFAULT_FOLDER_NAME = "DocScannerCloud"
    private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

    private const val PREFS_NAME = "drive_sync"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_FOLDER_NAME = "folder_name"
    private const val KEY_FOLDER_ID = "folder_id"

    fun authorizationRequest(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .build()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun folderName(context: Context): String =
        prefs(context).getString(KEY_FOLDER_NAME, null)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_FOLDER_NAME

    fun setFolderName(context: Context, name: String) {
        val trimmed = name.trim().ifEmpty { DEFAULT_FOLDER_NAME }
        if (trimmed == folderName(context)) return
        prefs(context).edit()
            .putString(KEY_FOLDER_NAME, trimmed)
            // The cached id belongs to the previous folder.
            .remove(KEY_FOLDER_ID)
            .apply()
    }

    fun cachedFolderId(context: Context): String? =
        prefs(context).getString(KEY_FOLDER_ID, null)

    fun cacheFolderId(context: Context, id: String?) {
        prefs(context).edit().apply {
            if (id == null) remove(KEY_FOLDER_ID) else putString(KEY_FOLDER_ID, id)
        }.apply()
    }

    /**
     * Queues one capture for upload. WorkManager waits for connectivity and
     * retries with backoff, so captures taken offline still make it to Drive.
     */
    fun enqueueUpload(context: Context, uri: Uri, displayName: String) {
        val request = OneTimeWorkRequestBuilder<DriveUploadWorker>()
            .setInputData(
                workDataOf(
                    DriveUploadWorker.KEY_URI to uri.toString(),
                    DriveUploadWorker.KEY_NAME to displayName,
                ),
            )
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
