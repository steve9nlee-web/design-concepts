package com.perimeter.attendance.net

import com.perimeter.attendance.data.Session
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Posts one session to the Apps Script web app (AppendSession.gs).
 *
 * HttpURLConnection rather than OkHttp/Retrofit on purpose: it ships with the
 * platform, so this is one fewer dependency that can fail a first Gradle sync.
 *
 * Caveat worth knowing: the shared secret lives on the handset, so anyone who
 * pulls the APK apart can post rows as any staff_id. HANDOFF.md section 6 puts a
 * real server in between for exactly this reason. Until that exists, treat the
 * sheet as trustworthy for honest staff and auditable rather than tamper-proof.
 */
object AppendSessionClient {

    data class Result(val ok: Boolean, val message: String)

    fun post(endpointUrl: String, sharedSecret: String, session: Session): Result {
        if (endpointUrl.isBlank()) {
            return Result(false, "No endpoint configured")
        }
        var conn: HttpURLConnection? = null
        return try {
            val payload = session.toJson().apply { put("secret", sharedSecret) }
            conn = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 20_000
                doOutput = true
                // Apps Script redirects /exec to a googleusercontent host.
                instanceFollowRedirects = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            if (code !in 200..299) return Result(false, "HTTP $code")

            // Apps Script answers 200 even for handled errors; read the body.
            val ok = try {
                JSONObject(body).optString("status") == "ok"
            } catch (e: Exception) {
                false
            }
            Result(ok, if (ok) "written" else body.take(180))
        } catch (e: Exception) {
            Result(false, e.message ?: "network error")
        } finally {
            conn?.disconnect()
        }
    }
}
