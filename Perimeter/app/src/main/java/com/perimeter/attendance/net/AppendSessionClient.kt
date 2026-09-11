package com.perimeter.attendance.net

import com.perimeter.attendance.data.Session
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Posts one session to the backend webhook.
 *
 * Built for an **n8n** webhook (see `n8n/README.md`), which is a better fit than
 * calling Apps Script from the handset: n8n holds the Google credentials, so the
 * phone never carries anything that can reach Drive. That is what HANDOFF.md
 * section 6 asks for.
 *
 * Still true, and still worth saying: the bearer token below ships inside the
 * APK. It stops casual noise reaching your webhook; it does not prove the phone
 * is the phone it claims to be. n8n should treat every field as a claim and
 * stamp its own receipt time alongside them.
 *
 * HttpURLConnection rather than OkHttp/Retrofit on purpose — it ships with the
 * platform, so this is one fewer dependency that can fail a Gradle sync.
 */
object AppendSessionClient {

    data class Result(val ok: Boolean, val message: String)

    fun post(endpointUrl: String, token: String, session: Session): Result {
        if (endpointUrl.isBlank()) {
            return Result(false, "No webhook URL configured")
        }
        var conn: HttpURLConnection? = null
        return try {
            val payload = session.toJson()
            conn = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 30_000   // a cold n8n workflow can take a few seconds
                doOutput = true
                instanceFollowRedirects = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                // n8n Header Auth. Configure the credential in n8n with header
                // name "x-perimeter-token" and this same value.
                if (token.isNotBlank()) {
                    setRequestProperty("x-perimeter-token", token)
                }
            }
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            when {
                // n8n answers 401 when the header token is wrong, and 404 when
                // the workflow is saved but not active. Both are worth naming,
                // because "it silently never syncs" is the hardest thing to
                // diagnose from a staff phone.
                code == 401 || code == 403 -> Result(false, "Rejected — check the token (HTTP $code)")
                code == 404 -> Result(false, "Webhook not found — is the workflow Active?")
                code !in 200..299 -> Result(false, "HTTP $code ${body.take(120)}")
                else -> {
                    // A 2xx from n8n means the workflow was accepted. Only treat
                    // it as a failure if the body explicitly says so — the
                    // default reply is {"message":"Workflow was started"}, which
                    // carries no status field, and demanding status=ok here
                    // would re-queue every row forever.
                    val explicitFailure = try {
                        JSONObject(body).optString("status").equals("error", ignoreCase = true)
                    } catch (e: Exception) {
                        false
                    }
                    if (explicitFailure) Result(false, body.take(180))
                    else Result(true, "written")
                }
            }
        } catch (e: Exception) {
            Result(false, e.message ?: "network error")
        } finally {
            conn?.disconnect()
        }
    }
}
