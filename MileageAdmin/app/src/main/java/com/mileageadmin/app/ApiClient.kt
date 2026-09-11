package com.mileageadmin.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class AdminClaim(
    val submittedAt: String,
    val date: String,
    val time: String,
    val staff: String,
    val destination: String,
    val purpose: String,
    val km: Double,
)

data class ClaimsResponse(
    val claims: List<AdminClaim>,
    val totalKm: Double,
    val count: Int,
    val xlsxUrl: String,
)

/** Minimal JSON client for the Apps Script web app's list/ping endpoints. */
object ApiClient {

    private const val TIMEOUT_MS = 20_000

    suspend fun fetchClaims(baseUrl: String, token: String): Result<ClaimsResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = JSONObject(getUrl(actionUrl(baseUrl, token, "list")))
                if (!json.optBoolean("ok")) {
                    throw IllegalStateException(json.optString("error", "Server rejected the request"))
                }
                val arr = json.optJSONArray("claims") ?: org.json.JSONArray()
                val claims = buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        add(
                            AdminClaim(
                                submittedAt = o.optString("submittedAt"),
                                date = o.optString("date"),
                                time = o.optString("time"),
                                staff = o.optString("staff"),
                                destination = o.optString("destination"),
                                purpose = o.optString("purpose"),
                                km = o.optDouble("km", 0.0),
                            )
                        )
                    }
                }
                ClaimsResponse(
                    claims = claims,
                    totalKm = json.optDouble("totalKm", claims.sumOf { it.km }),
                    count = json.optInt("count", claims.size),
                    xlsxUrl = json.optString("xlsxUrl"),
                )
            }
        }

    suspend fun ping(baseUrl: String, token: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = JSONObject(getUrl(actionUrl(baseUrl, token, "ping")))
                if (!json.optBoolean("ok")) {
                    throw IllegalStateException(json.optString("error", "Server rejected the token"))
                }
            }
        }

    private fun actionUrl(baseUrl: String, token: String, action: String): String =
        "$baseUrl${if (baseUrl.contains('?')) "&" else "?"}action=$action&token=" +
            URLEncoder.encode(token, "UTF-8")

    private fun getUrl(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            // Apps Script serves GET responses via a redirect to another host,
            // which HttpURLConnection refuses to follow automatically.
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
