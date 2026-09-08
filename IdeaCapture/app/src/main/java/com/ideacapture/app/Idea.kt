package com.ideacapture.app

import org.json.JSONObject
import java.util.UUID

enum class IdeaStatus(val label: String) {
    URGENT("Urgent"),
    NOT_SO_URGENT("Not so urgent"),
    WIP("WIP");

    companion object {
        fun fromName(name: String?): IdeaStatus =
            entries.firstOrNull { it.name == name } ?: NOT_SO_URGENT
    }
}

data class Idea(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var description: String = "",
    var status: IdeaStatus = IdeaStatus.NOT_SO_URGENT,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("description", description)
        put("status", status.name)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(json: JSONObject): Idea = Idea(
            id = json.optString("id", UUID.randomUUID().toString()),
            title = json.optString("title"),
            description = json.optString("description"),
            status = IdeaStatus.fromName(json.optString("status")),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
        )
    }
}
