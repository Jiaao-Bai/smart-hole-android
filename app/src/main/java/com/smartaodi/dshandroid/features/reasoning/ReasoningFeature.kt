package com.smartaodi.dshandroid.features.reasoning

import org.json.JSONObject

enum class AssistantDeltaKind { Text, Reasoning }

data class AssistantDelta(
    val kind: AssistantDeltaKind,
    val text: String,
)

/** Decodes only the DSH reasoning/text streaming vocabulary owned by this feature. */
object ReasoningFeatureCodec {
    fun assistantDelta(event: JSONObject): AssistantDelta? {
        if (event.optString("type") != "assistant/chunk") return null
        val chunk = event.optJSONObject("data")?.optJSONObject("chunk") ?: return null
        val kind = when (chunk.optString("type")) {
            "text-delta" -> AssistantDeltaKind.Text
            "reasoning-delta" -> AssistantDeltaKind.Reasoning
            else -> return null
        }
        return chunk.optString("text").takeIf(String::isNotEmpty)?.let { AssistantDelta(kind, it) }
    }
}
