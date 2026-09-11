package com.scamcallguard.app.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class BlockEntry(
    val number: String,
    val label: String,
    val category: String,
    val source: String,
    val addedAt: Long
)

data class ScreenedCall(
    val id: Long,
    val number: String,
    val label: String,
    val action: String,
    val timestamp: Long
)

/**
 * Local store for the scam-number blocklist and the history of screened calls.
 * Numbers are stored normalized (digits only, see [PhoneNumbers.normalize]).
 */
class BlocklistDb(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "scamguard.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE blocklist (
                number TEXT PRIMARY KEY,
                label TEXT NOT NULL,
                category TEXT NOT NULL,
                source TEXT NOT NULL,
                added_at INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE call_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                number TEXT NOT NULL,
                label TEXT NOT NULL,
                action TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun lookup(rawNumber: String): BlockEntry? {
        val normalized = PhoneNumbers.normalize(rawNumber) ?: return null
        readableDatabase.query(
            "blocklist", null, "number = ?", arrayOf(normalized), null, null, null
        ).use { c ->
            if (!c.moveToFirst()) return null
            return BlockEntry(
                number = c.getString(c.getColumnIndexOrThrow("number")),
                label = c.getString(c.getColumnIndexOrThrow("label")),
                category = c.getString(c.getColumnIndexOrThrow("category")),
                source = c.getString(c.getColumnIndexOrThrow("source")),
                addedAt = c.getLong(c.getColumnIndexOrThrow("added_at"))
            )
        }
    }

    fun upsert(entry: BlockEntry): Boolean {
        val normalized = PhoneNumbers.normalize(entry.number) ?: return false
        val values = ContentValues().apply {
            put("number", normalized)
            put("label", entry.label)
            put("category", entry.category)
            put("source", entry.source)
            put("added_at", entry.addedAt)
        }
        writableDatabase.insertWithOnConflict(
            "blocklist", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
        return true
    }

    fun remove(rawNumber: String) {
        val normalized = PhoneNumbers.normalize(rawNumber) ?: return
        writableDatabase.delete("blocklist", "number = ?", arrayOf(normalized))
    }

    fun removeBySource(source: String) {
        writableDatabase.delete("blocklist", "source = ?", arrayOf(source))
    }

    fun blocklistCount(): Long =
        readableDatabase.compileStatement("SELECT COUNT(*) FROM blocklist")
            .use { it.simpleQueryForLong() }

    fun allEntries(): List<BlockEntry> {
        val entries = mutableListOf<BlockEntry>()
        readableDatabase.query(
            "blocklist", null, null, null, null, null, "added_at DESC"
        ).use { c ->
            while (c.moveToNext()) {
                entries += BlockEntry(
                    number = c.getString(c.getColumnIndexOrThrow("number")),
                    label = c.getString(c.getColumnIndexOrThrow("label")),
                    category = c.getString(c.getColumnIndexOrThrow("category")),
                    source = c.getString(c.getColumnIndexOrThrow("source")),
                    addedAt = c.getLong(c.getColumnIndexOrThrow("added_at"))
                )
            }
        }
        return entries
    }

    fun logCall(number: String, label: String, action: String) {
        val values = ContentValues().apply {
            put("number", number)
            put("label", label)
            put("action", action)
            put("timestamp", System.currentTimeMillis())
        }
        writableDatabase.insert("call_history", null, values)
    }

    fun recentCalls(limit: Int = 100): List<ScreenedCall> {
        val calls = mutableListOf<ScreenedCall>()
        readableDatabase.query(
            "call_history", null, null, null, null, null, "timestamp DESC", limit.toString()
        ).use { c ->
            while (c.moveToNext()) {
                calls += ScreenedCall(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    number = c.getString(c.getColumnIndexOrThrow("number")),
                    label = c.getString(c.getColumnIndexOrThrow("label")),
                    action = c.getString(c.getColumnIndexOrThrow("action")),
                    timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"))
                )
            }
        }
        return calls
    }

    companion object {
        @Volatile
        private var instance: BlocklistDb? = null

        fun get(context: Context): BlocklistDb =
            instance ?: synchronized(this) {
                instance ?: BlocklistDb(context).also { instance = it }
            }
    }
}
