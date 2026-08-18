package com.smartaodi.dshandroid.features.commands

import org.json.JSONArray

data class CommandDescriptor(
    val name: String,
    val description: String,
    val inputHint: String? = null,
)

data class CommandsFeatureState(
    val items: List<CommandDescriptor> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
) {
    fun matching(draft: String): List<CommandDescriptor> {
        if (!draft.startsWith("/") || draft.drop(1).contains(' ')) return emptyList()
        val token = draft.drop(1).lowercase()
        return items.filter { it.name.startsWith(token) }.take(8)
    }
}

object CommandsFeatureCodec {
    fun directory(value: Any?): List<CommandDescriptor> {
        val items = value as? JSONArray ?: error("commands/list returned a non-array value")
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val name = item.optString("name").takeIf(String::isNotBlank) ?: continue
                add(
                    CommandDescriptor(
                        name = name,
                        description = item.optString("description"),
                        inputHint = item.optJSONObject("input")?.optString("hint")?.takeIf(String::isNotBlank),
                    ),
                )
            }
        }.sortedBy(CommandDescriptor::name)
    }
}
