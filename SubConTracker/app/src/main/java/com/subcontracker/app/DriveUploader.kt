package com.subcontracker.app

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Uploads SubConTracker.xlsx to the signed-in user's Drive via the REST API.
 * Creates the file on first upload, updates it in place afterwards.
 * Must be called from a background thread.
 */
object DriveUploader {

    private const val XLSX_MIME =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun upload(context: Context, account: GoogleSignInAccount, file: File) {
        val androidAccount = account.account
            ?: throw IllegalStateException("No Android account on sign-in result")
        val token = GoogleAuthUtil.getToken(
            context, androidAccount, "oauth2:${SessionManager.DRIVE_FILE_SCOPE}"
        )

        val existingId = findExistingFileId(token)
        if (existingId == null) createFile(token, file) else updateFile(token, existingId, file)
    }

    private fun findExistingFileId(token: String): String? {
        val query = URLEncoder.encode(
            "name='${ExcelManager.FILE_NAME}' and trashed=false", "UTF-8"
        )
        val conn = openConnection(
            "https://www.googleapis.com/drive/v3/files?q=$query&fields=files(id)", "GET", token
        )
        conn.inputStream.use { stream ->
            val files = JSONObject(stream.bufferedReader().readText()).getJSONArray("files")
            return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
        }
    }

    private fun createFile(token: String, file: File) {
        val metadata = JSONObject().put("name", ExcelManager.FILE_NAME).toString()
        val boundary = "subcontracker-${System.currentTimeMillis()}"
        val conn = openConnection(
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart",
            "POST", token
        )
        conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        conn.doOutput = true
        conn.outputStream.use { out ->
            out.write(
                ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
                    "$metadata\r\n--$boundary\r\nContent-Type: $XLSX_MIME\r\n\r\n")
                    .toByteArray()
            )
            file.inputStream().use { it.copyTo(out) }
            out.write("\r\n--$boundary--".toByteArray())
        }
        checkSuccess(conn)
    }

    private fun updateFile(token: String, fileId: String, file: File) {
        val conn = openConnection(
            "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media",
            "PATCH", token
        )
        conn.setRequestProperty("Content-Type", XLSX_MIME)
        conn.doOutput = true
        conn.outputStream.use { out -> file.inputStream().use { it.copyTo(out) } }
        checkSuccess(conn)
    }

    private fun openConnection(url: String, method: String, token: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        if (method == "PATCH") {
            // HttpURLConnection has no native PATCH; Drive honors the override header
            conn.requestMethod = "POST"
            conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        } else {
            conn.requestMethod = method
        }
        conn.setRequestProperty("Authorization", "Bearer $token")
        return conn
    }

    private fun checkSuccess(conn: HttpURLConnection) {
        if (conn.responseCode !in 200..299) {
            val error = conn.errorStream?.bufferedReader()?.readText() ?: ""
            throw IllegalStateException("Drive upload failed (${conn.responseCode}): $error")
        }
        conn.inputStream.close()
    }
}
