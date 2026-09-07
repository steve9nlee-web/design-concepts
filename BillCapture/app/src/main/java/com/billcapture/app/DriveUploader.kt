package com.billcapture.app

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Uploads Bill_Capture.xlsx to the signed-in user's Drive via the REST API.
 * Creates the file on first upload, updates it in place afterwards.
 * Must be called from a background thread.
 */
object DriveUploader {

    private const val XLSX_MIME =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private const val FOLDER_MIME = "application/vnd.google-apps.folder"
    private const val PHOTOS_FOLDER = "BillCapture Photos"

    fun upload(context: Context, account: GoogleSignInAccount, file: File) {
        val token = fetchToken(context, account)
        val existingId = findExistingFileId(token, ExcelManager.FILE_NAME)
        if (existingId == null) {
            createFile(token, file, ExcelManager.FILE_NAME, XLSX_MIME)
        } else {
            updateFile(token, existingId, file)
        }
    }

    /** Uploads a bill photo into the "BillCapture Photos" Drive folder. */
    fun uploadPhoto(
        context: Context,
        account: GoogleSignInAccount,
        file: File,
        name: String
    ) {
        val token = fetchToken(context, account)
        val folderId = findOrCreatePhotosFolder(token)
        createFile(token, file, name, "image/jpeg", folderId)
    }

    private fun fetchToken(context: Context, account: GoogleSignInAccount): String {
        val androidAccount = account.account
            ?: throw IllegalStateException("No Android account on sign-in result")
        return GoogleAuthUtil.getToken(
            context, androidAccount, "oauth2:${SessionManager.DRIVE_FILE_SCOPE}"
        )
    }

    private fun findExistingFileId(token: String, name: String, mime: String? = null): String? {
        val mimeClause = if (mime != null) " and mimeType='$mime'" else ""
        val query = URLEncoder.encode(
            "name='$name'$mimeClause and trashed=false", "UTF-8"
        )
        val conn = openConnection(
            "https://www.googleapis.com/drive/v3/files?q=$query&fields=files(id)", "GET", token
        )
        conn.inputStream.use { stream ->
            val files = JSONObject(stream.bufferedReader().readText()).getJSONArray("files")
            return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
        }
    }

    private fun findOrCreatePhotosFolder(token: String): String {
        findExistingFileId(token, PHOTOS_FOLDER, FOLDER_MIME)?.let { return it }
        val metadata = JSONObject()
            .put("name", PHOTOS_FOLDER)
            .put("mimeType", FOLDER_MIME)
            .toString()
        val conn = openConnection(
            "https://www.googleapis.com/drive/v3/files?fields=id", "POST", token
        )
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        conn.doOutput = true
        conn.outputStream.use { it.write(metadata.toByteArray()) }
        checkResponseCode(conn)
        conn.inputStream.use { stream ->
            return JSONObject(stream.bufferedReader().readText()).getString("id")
        }
    }

    private fun createFile(
        token: String,
        file: File,
        name: String,
        mime: String,
        parentId: String? = null
    ) {
        val metadata = JSONObject().put("name", name)
        if (parentId != null) {
            metadata.put("parents", org.json.JSONArray().put(parentId))
        }
        val boundary = "billcapture-${System.currentTimeMillis()}"
        val conn = openConnection(
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart",
            "POST", token
        )
        conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        conn.doOutput = true
        conn.outputStream.use { out ->
            out.write(
                ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
                    "$metadata\r\n--$boundary\r\nContent-Type: $mime\r\n\r\n")
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

    private fun checkResponseCode(conn: HttpURLConnection) {
        if (conn.responseCode !in 200..299) {
            val error = conn.errorStream?.bufferedReader()?.readText() ?: ""
            throw IllegalStateException("Drive upload failed (${conn.responseCode}): $error")
        }
    }

    private fun checkSuccess(conn: HttpURLConnection) {
        checkResponseCode(conn)
        conn.inputStream.close()
    }
}
