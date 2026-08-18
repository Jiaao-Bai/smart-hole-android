package com.smartaodi.dshandroid.features.queue

import com.smartaodi.dshandroid.protocol.DshServerEvent
import org.json.JSONArray
import org.json.JSONObject

enum class PromptMode(val wireValue: String) {
    Queue("queue"),
    Steer("steer"),
}

enum class QueuePlacement {
    Queued,
    Steering,
    Context,
}

data class QueueItem(
    val id: String,
    val placement: QueuePlacement,
    val text: String,
)

data class QueueFeatureState(
    val items: List<QueueItem> = emptyList(),
    val operationsInProgress: Set<String> = emptySet(),
) {
    val queued: List<QueueItem> get() = items.filter { it.placement == QueuePlacement.Queued }
    val steering: List<QueueItem> get() = items.filter { it.placement == QueuePlacement.Steering }
}

/** Owns the complete `session/queue` snapshot contract; callers never branch on queue JSON. */
object QueueFeatureCodec {
    fun snapshot(event: DshServerEvent, selectedSessionId: String?): List<QueueItem>? {
        if (event.method != "session/queue") return null
        if (event.payload.optString("sessionId") != selectedSessionId) return null
        val items = event.payload.optJSONArray("items") ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("id").takeIf(String::isNotBlank) ?: continue
                val placement = when (item.optString("placement")) {
                    "queued" -> QueuePlacement.Queued
                    "steering" -> QueuePlacement.Steering
                    "context" -> QueuePlacement.Context
                    else -> continue
                }
                val content = item.optJSONObject("message")?.optJSONArray("content")
                add(QueueItem(id, placement, visibleText(content)))
            }
        }
    }

    fun textContent(text: String): JSONArray = JSONArray().put(
        JSONObject().put("type", "text").put("text", text),
    )

    private fun visibleText(content: JSONArray?): String = buildList {
        if (content == null) return@buildList
        for (index in 0 until content.length()) {
            val block = content.optJSONObject(index) ?: continue
            if (block.optString("type") == "text") {
                block.optString("text").takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }.joinToString("\n\n")
}
