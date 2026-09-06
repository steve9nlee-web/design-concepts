package com.scamcallguard.app.sync

import android.content.Context
import com.scamcallguard.app.db.BlockEntry
import com.scamcallguard.app.db.BlocklistDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a community blocklist from a user-configured HTTPS URL and merges
 * it into the local database, replacing previous entries from the same feed.
 *
 * Expected JSON format:
 * {
 *   "entries": [
 *     {"number": "+15550100", "label": "IRS impersonation", "category": "scam"}
 *   ]
 * }
 *
 * This is also the integration point for a licensed caller-ID API (for example
 * Truecaller for Business/Partners): implement the same fetch-and-merge here
 * against the vendor's authorized endpoint. Scraping an unauthorized feed of a
 * proprietary caller-ID database is not supported.
 */
object BlocklistUpdater {

    const val SOURCE_REMOTE = "remote_feed"

    sealed class Result {
        data class Success(val imported: Int) : Result()
        data class Failure(val message: String) : Result()
    }

    suspend fun update(context: Context, feedUrl: String): Result = withContext(Dispatchers.IO) {
        val url = try {
            URL(feedUrl)
        } catch (e: Exception) {
            return@withContext Result.Failure("Invalid URL")
        }
        if (url.protocol != "https") {
            return@withContext Result.Failure("Feed URL must use https")
        }

        val body = try {
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            try {
                if (conn.responseCode != 200) {
                    return@withContext Result.Failure("Feed returned HTTP ${conn.responseCode}")
                }
                conn.inputStream.bufferedReader().readText()
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            return@withContext Result.Failure(e.message ?: "Network error")
        }

        val entries = try {
            parse(body)
        } catch (e: Exception) {
            return@withContext Result.Failure("Feed is not valid blocklist JSON")
        }

        val db = BlocklistDb.get(context)
        db.removeBySource(SOURCE_REMOTE)
        var imported = 0
        val now = System.currentTimeMillis()
        for (entry in entries) {
            if (db.upsert(entry.copy(source = SOURCE_REMOTE, addedAt = now))) imported++
        }
        Result.Success(imported)
    }

    private fun parse(body: String): List<BlockEntry> {
        val root = JSONObject(body)
        val array = root.getJSONArray("entries")
        val entries = mutableListOf<BlockEntry>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            entries += BlockEntry(
                number = obj.getString("number"),
                label = obj.optString("label", "Reported scam number"),
                category = obj.optString("category", "scam"),
                source = SOURCE_REMOTE,
                addedAt = 0L
            )
        }
        return entries
    }
}
