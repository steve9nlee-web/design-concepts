package com.mileageclaim.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal JSON client for the Apps Script web app. Apps Script answers a POST
 * with a 302 to a one-time googleusercontent URL, so redirects are followed
 * manually (HttpURLConnection won't hop hosts on a POST).
 */
object ApiClient {

    private const val TIMEOUT_MS = 20_000

    suspend fun submit(baseUrl: String, token: String, claim: Claim): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject()
                    .put("token", token)
                    .put("date", claim.date)
                    .put("time", claim.time)
                    .put("staff", claim.staff)
                    .put("destination", claim.destination)
                    .put("purpose", claim.purpose)
                    .put("km", claim.km)
                val response = postJson(baseUrl, body.toString())
                val json = JSONObject(response)
                if (!json.optBoolean("ok")) {
                    throw IllegalStateException(json.optString("error", "Server rejected the claim"))
                }
            }
        }

    suspend fun ping(baseUrl: String, token: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$baseUrl${if (baseUrl.contains('?')) "&" else "?"}action=ping&token=" +
                    java.net.URLEncoder.encode(token, "UTF-8")
                val json = JSONObject(getUrl(url))
                if (!json.optBoolean("ok")) {
                    throw IllegalStateException(json.optString("error", "Server rejected the token"))
                }
            }
        }

    private fun postJson(urlString: String, body: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code in 301..303) {
                val location = conn.getHeaderField("Location")
                    ?: throw IllegalStateException("Redirect without Location header")
                return getUrl(location)
            }
            if (code !in 200..299) throw IllegalStateException("HTTP $code from server")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun getUrl(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            // Apps Script can answer with a redirect to another host, which
            // HttpURLConnection may refuse to follow automatically.
            if (code in 301..303) {
                val location = conn.getHeaderField("Location")
                    ?: throw IllegalStateException("Redirect without Location header")
                return getUrl(location)
            }
            if (code !in 200..299) throw IllegalStateException("HTTP $code from server")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
