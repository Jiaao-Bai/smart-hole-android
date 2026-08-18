package com.smartaodi.dshandroid.protocol

import org.json.JSONArray
import org.json.JSONObject

/**
 * Android-native adapter for the public DSH Host surface contract.
 *
 * DSH plugins own projections and tool presentation. This adapter only decodes
 * the Host wire into product-neutral Kotlin models and retains every unknown
 * presentation as raw JSON for forward-compatible fallback rendering.
 */
object DshSurfaceAdapter {
    const val SUPPORTED_DSH_VERSION = "0.1.0-rc.7"
    const val SUPPORTED_HOST_API_VERSION = "0.0.1"

    fun requireCompatibleDshVersion(version: String) {
        if (version != SUPPORTED_DSH_VERSION) {
            throw DshProtocolException(
                "DSH $version is incompatible with this Smart Hole build; expected $SUPPORTED_DSH_VERSION",
            )
        }
    }

    fun requireCompatibleHostApiVersion(version: String) {
        if (version != SUPPORTED_HOST_API_VERSION) {
            throw DshProtocolException(
                "DSH Host API $version is incompatible with this Smart Hole build; expected $SUPPORTED_HOST_API_VERSION",
            )
        }
    }

    fun projectionSnapshot(value: JSONObject?): SessionProjectionSnapshot? {
        value ?: return null
        val asOfSeq = value.optLong("asOfSeq", -1L)
        val rawValues = value.optJSONObject("values") ?: return SessionProjectionSnapshot(asOfSeq, emptyMap())
        val values = buildMap {
            for (key in rawValues.keys()) {
                put(key, ProjectionValue(asOfSeq, jsonValue(rawValues.opt(key))))
            }
        }
        return SessionProjectionSnapshot(asOfSeq, values)
    }

    fun projectionValue(value: Any?): String = jsonValue(value)

    fun toolPresentation(envelope: JSONObject?, expectedFor: String): ToolPresentation? {
        if (envelope == null || envelope.optString("for") != expectedFor) return null
        val view = envelope.optJSONObject("view") ?: return null
        val card = view.optString("card").takeIf { it.isNotBlank() } ?: return null
        return ToolPresentation(
            card = card,
            title = view.stringOrNull("title"),
            kind = view.stringOrNull("kind"),
            description = view.stringOrNull("description"),
            rawInput = view.opt("rawInput").takeUnless { it == null || it === JSONObject.NULL }?.let(::prettyValue),
            content = contentText(view.optJSONArray("content")),
            cwd = view.stringOrNull("cwd"),
            output = view.stringOrNull("output"),
            exitCode = view.intOrNull("exitCode"),
            signal = view.stringOrNull("signal"),
            locations = parseLocations(view.optJSONArray("locations")),
            diffs = parseDiffs(view.optJSONArray("diffs")),
            search = parseSearch(card, view),
            read = parseRead(card, view),
            web = parseWeb(card, view),
            rawJson = view.toString(2),
        )
    }

    private fun parseLocations(items: JSONArray?): List<ToolFileLocation> = buildList {
        if (items == null) return@buildList
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val path = item.stringOrNull("path") ?: continue
            add(ToolFileLocation(path, item.intOrNull("line")))
        }
    }

    private fun parseDiffs(items: JSONArray?): List<ToolFileDiff> = buildList {
        if (items == null) return@buildList
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val path = item.stringOrNull("path") ?: continue
            val newText = item.stringOrNull("newText") ?: continue
            val oldText = item.opt("oldText").takeUnless { it == null || it === JSONObject.NULL } as? String
            add(ToolFileDiff(path, oldText, newText))
        }
    }

    private fun parseSearch(card: String, view: JSONObject): ToolSearchPresentation? {
        if (card != "search") return null
        val shape = view.stringOrNull("shape") ?: return null
        val paths = buildList {
            val items = view.optJSONArray("paths") ?: return@buildList
            for (index in 0 until items.length()) items.optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
        val files = buildList {
            val items = view.optJSONArray("files") ?: return@buildList
            for (index in 0 until items.length()) {
                val file = items.optJSONObject(index) ?: continue
                val path = file.stringOrNull("path") ?: continue
                val matches = buildList {
                    val matchItems = file.optJSONArray("matches") ?: return@buildList
                    for (matchIndex in 0 until matchItems.length()) {
                        val match = matchItems.optJSONObject(matchIndex) ?: continue
                        val lineNumber = match.intOrNull("lineNumber") ?: continue
                        val line = match.stringOrNull("line") ?: continue
                        add(ToolSearchMatch(lineNumber, line))
                    }
                }
                add(ToolSearchFile(path, matches))
            }
        }
        return ToolSearchPresentation(
            shape = shape,
            paths = paths,
            files = files,
            truncated = view.optBoolean("truncated", false),
            total = view.intOrNull("total"),
        )
    }

    private fun parseRead(card: String, view: JSONObject): ToolReadPresentation? {
        if (card != "read") return null
        val path = view.stringOrNull("path") ?: return null
        val offset = view.intOrNull("offset") ?: return null
        val totalLines = view.intOrNull("totalLines") ?: return null
        val lines = buildList {
            val items = view.optJSONArray("lines") ?: return@buildList
            for (index in 0 until items.length()) {
                val line = items.optJSONObject(index) ?: continue
                val number = line.intOrNull("number") ?: continue
                val text = line.stringOrNull("text") ?: continue
                add(ToolReadLine(number, text))
            }
        }
        return ToolReadPresentation(path, offset, lines, totalLines, view.stringOrNull("lang"))
    }

    private fun parseWeb(card: String, view: JSONObject): ToolWebPresentation? {
        if (card != "web") return null
        val kind = view.stringOrNull("kind") ?: return null
        val sources = buildList {
            val items = view.optJSONArray("sources") ?: return@buildList
            for (index in 0 until items.length()) {
                val source = items.optJSONObject(index) ?: continue
                val url = source.stringOrNull("url") ?: continue
                add(
                    ToolWebSource(
                        url = url,
                        title = source.stringOrNull("title"),
                        snippet = source.stringOrNull("snippet"),
                        publishedAt = source.stringOrNull("publishedAt"),
                    ),
                )
            }
        }
        return ToolWebPresentation(
            kind = kind,
            url = view.stringOrNull("url"),
            statusCode = view.intOrNull("statusCode"),
            sources = sources,
            answer = view.stringOrNull("answer"),
            truncated = view.optBoolean("truncated", false),
        )
    }

    private fun contentText(content: JSONArray?): String? {
        if (content == null) return null
        return buildList {
            for (index in 0 until content.length()) {
                val block = content.optJSONObject(index) ?: continue
                when (block.optString("type")) {
                    "text" -> block.stringOrNull("text")?.let(::add)
                    else -> add(block.toString(2))
                }
            }
        }.joinToString("\n\n").takeIf { it.isNotBlank() }
    }

    private fun JSONObject.stringOrNull(key: String): String? = optString(key)
        .takeIf { has(key) && !isNull(key) && it.isNotBlank() }

    private fun JSONObject.intOrNull(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null

    private fun prettyValue(value: Any): String = when (value) {
        is JSONObject -> value.toString(2)
        is JSONArray -> value.toString(2)
        else -> value.toString()
    }

    private fun jsonValue(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.toString()
        is JSONArray -> value.toString()
        is String -> JSONObject.quote(value)
        is Number, is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString())
    }
}
