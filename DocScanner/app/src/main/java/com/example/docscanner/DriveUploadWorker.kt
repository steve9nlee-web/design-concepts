package com.example.docscanner

import android.content.Context
import android.net.Uri
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.tasks.Tasks
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Uploads one captured document to the user's chosen Drive folder via the
 * Drive v3 REST API (multipart upload). The folder is found-or-created by name
 * at the Drive root and its id cached; with the drive.file scope the search
 * only ever matches folders this app created.
 */
class DriveUploadWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val context = applicationContext
        if (!DriveSync.isEnabled(context)) return Result.success()

        val uri = inputData.getString(KEY_URI)?.let(Uri::parse) ?: return Result.failure()
        val name = inputData.getString(KEY_NAME) ?: return Result.failure()

        val token = try {
            val auth = Tasks.await(
                Identity.getAuthorizationClient(context).authorize(DriveSync.authorizationRequest()),
            )
            // A resolution means the user must re-consent, which needs UI we
            // don't have in the background — give up rather than retry forever.
            if (auth.hasResolution()) return Result.failure()
            auth.accessToken ?: return retryOrFail()
        } catch (e: Exception) {
            return retryOrFail()
        }

        return try {
            val folderId = DriveSync.cachedFolderId(context)
                ?: findOrCreateFolder(token, DriveSync.folderName(context))
                    .also { DriveSync.cacheFolderId(context, it) }

            when (uploadFile(token, folderId, name, uri)) {
                UploadOutcome.OK -> Result.success()
                UploadOutcome.FOLDER_GONE -> {
                    // Folder was deleted in Drive since we cached it; recreate next attempt.
                    DriveSync.cacheFolderId(context, null)
                    retryOrFail()
                }
            }
        } catch (e: IOException) {
            retryOrFail()
        }
    }

    private fun retryOrFail(): Result =
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()

    private fun findOrCreateFolder(token: String, name: String): String {
        val escaped = name.replace("\\", "\\\\").replace("'", "\\'")
        val query = URLEncoder.encode(
            "name = '$escaped' and mimeType = 'application/vnd.google-apps.folder' and trashed = false",
            "UTF-8",
        )
        val search = URL("$FILES_URL?q=$query&fields=files(id)&spaces=drive")
            .openConnection() as HttpURLConnection
        search.setRequestProperty("Authorization", "Bearer $token")
        val existing = search.readJson().getJSONArray("files")
        if (existing.length() > 0) return existing.getJSONObject(0).getString("id")

        val create = URL(FILES_URL).openConnection() as HttpURLConnection
        create.requestMethod = "POST"
        create.doOutput = true
        create.setRequestProperty("Authorization", "Bearer $token")
        create.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        create.outputStream.use { out ->
            out.write(
                JSONObject()
                    .put("name", name)
                    .put("mimeType", "application/vnd.google-apps.folder")
                    .toString()
                    .toByteArray(),
            )
        }
        return create.readJson().getString("id")
    }

    private enum class UploadOutcome { OK, FOLDER_GONE }

    private fun uploadFile(token: String, folderId: String, name: String, uri: Uri): UploadOutcome {
        val metadata = JSONObject()
            .put("name", name)
            .put("parents", JSONArray().put(folderId))
        val boundary = "docscanner-${System.currentTimeMillis()}"

        val conn = URL("$UPLOAD_URL?uploadType=multipart").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setChunkedStreamingMode(0)
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")

        try {
            conn.outputStream.use { out ->
                out.write(
                    ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
                        metadata.toString() +
                        "\r\n--$boundary\r\nContent-Type: image/jpeg\r\n\r\n").toByteArray(),
                )
                applicationContext.contentResolver.openInputStream(uri)?.use { it.copyTo(out) }
                    ?: throw IOException("Capture no longer readable: $uri")
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }
            return when (conn.responseCode) {
                in 200..299 -> UploadOutcome.OK
                HttpURLConnection.HTTP_NOT_FOUND -> UploadOutcome.FOLDER_GONE
                else -> throw IOException("Drive upload failed: HTTP ${conn.responseCode}")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun HttpURLConnection.readJson(): JSONObject =
        try {
            if (responseCode !in 200..299) throw IOException("Drive API error: HTTP $responseCode")
            inputStream.use { JSONObject(it.readBytes().decodeToString()) }
        } finally {
            disconnect()
        }

    companion object {
        const val KEY_URI = "uri"
        const val KEY_NAME = "name"
        private const val MAX_ATTEMPTS = 5
        private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
    }
}
