package com.ideacapture.app

import android.content.Context
import org.json.JSONArray

/** Simple local persistence backed by SharedPreferences + JSON. */
class IdeaStore(context: Context) {

    private val prefs = context.getSharedPreferences("ideas", Context.MODE_PRIVATE)

    fun loadAll(): MutableList<Idea> {
        val raw = prefs.getString(KEY_IDEAS, null) ?: return mutableListOf()
        return try {
            val array = JSONArray(raw)
            MutableList(array.length()) { Idea.fromJson(array.getJSONObject(it)) }
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveAll(ideas: List<Idea>) {
        val array = JSONArray()
        ideas.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_IDEAS, array.toString()).apply()
    }

    fun get(id: String): Idea? = loadAll().firstOrNull { it.id == id }

    fun upsert(idea: Idea) {
        val ideas = loadAll()
        val index = ideas.indexOfFirst { it.id == idea.id }
        if (index >= 0) {
            ideas[index] = idea
        } else {
            ideas.add(0, idea)
        }
        saveAll(ideas)
    }

    fun delete(id: String) {
        val ideas = loadAll()
        ideas.removeAll { it.id == id }
        saveAll(ideas)
    }

    companion object {
        private const val KEY_IDEAS = "ideas_json"
    }
}
