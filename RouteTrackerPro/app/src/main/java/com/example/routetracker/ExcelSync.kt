package com.example.routetracker

import android.content.Context
import android.net.Uri
import com.example.routetracker.db.TripStore

/**
 * Rewrites the linked Excel file (chosen once via the system file picker,
 * typically a file inside Google Drive) with the full trip log.
 * The Drive documents provider syncs the new content to the cloud.
 */
object ExcelSync {

    /** @return true when the workbook was written successfully. */
    fun syncIfConfigured(ctx: Context): Boolean {
        val uriStr = Prefs.getExcelUri(ctx) ?: return false
        return runCatching {
            val store = TripStore.get(ctx)
            val resolver = ctx.contentResolver
            // "wt" truncates so a shrinking log never leaves stale bytes behind.
            val stream = resolver.openOutputStream(Uri.parse(uriStr), "wt")
                ?: resolver.openOutputStream(Uri.parse(uriStr))
                ?: return false
            stream.use { out ->
                XlsxExporter.write(out, store.getTrips()) { id -> store.getPoints(id) }
            }
            true
        }.getOrDefault(false)
    }
}
