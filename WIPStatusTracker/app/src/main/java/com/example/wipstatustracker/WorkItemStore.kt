package com.example.wipstatustracker

import android.content.Context

class WorkItemStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): MutableList<WorkItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return mutableListOf()
        return runCatching { WorkItem.listFromJson(raw) }.getOrDefault(mutableListOf())
    }

    fun save(items: List<WorkItem>) {
        prefs.edit().putString(KEY_ITEMS, WorkItem.listToJson(items)).apply()
    }

    companion object {
        private const val PREFS_NAME = "wip_status_tracker"
        private const val KEY_ITEMS = "work_items"
    }
}
