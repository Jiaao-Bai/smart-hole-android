package com.smartaodi.dshandroid.protocol

data class ProjectionValue(
    val seq: Long,
    val json: String,
)

data class SessionProjectionSnapshot(
    val asOfSeq: Long,
    val values: Map<String, ProjectionValue>,
) {
    fun updated(key: String, valueJson: String, seq: Long): SessionProjectionSnapshot {
        val current = values[key]
        if (current != null && current.seq >= seq) return this
        return copy(
            asOfSeq = maxOf(asOfSeq, seq),
            values = values + (key to ProjectionValue(seq, valueJson)),
        )
    }
}

data class ToolPresentation(
    val card: String,
    val title: String? = null,
    val kind: String? = null,
    val description: String? = null,
    val rawInput: String? = null,
    val content: String? = null,
    val cwd: String? = null,
    val output: String? = null,
    val exitCode: Int? = null,
    val signal: String? = null,
    val locations: List<ToolFileLocation> = emptyList(),
    val diffs: List<ToolFileDiff> = emptyList(),
    val search: ToolSearchPresentation? = null,
    val read: ToolReadPresentation? = null,
    val web: ToolWebPresentation? = null,
    val rawJson: String,
)

data class ToolFileLocation(
    val path: String,
    val line: Int? = null,
)

data class ToolFileDiff(
    val path: String,
    val oldText: String?,
    val newText: String,
)

data class ToolSearchPresentation(
    val shape: String,
    val paths: List<String> = emptyList(),
    val files: List<ToolSearchFile> = emptyList(),
    val truncated: Boolean = false,
    val total: Int? = null,
)

data class ToolSearchFile(
    val path: String,
    val matches: List<ToolSearchMatch>,
)

data class ToolSearchMatch(
    val lineNumber: Int,
    val line: String,
)

data class ToolReadPresentation(
    val path: String,
    val offset: Int,
    val lines: List<ToolReadLine>,
    val totalLines: Int,
    val language: String? = null,
)

data class ToolReadLine(
    val number: Int,
    val text: String,
)

data class ToolWebPresentation(
    val kind: String,
    val url: String? = null,
    val statusCode: Int? = null,
    val sources: List<ToolWebSource> = emptyList(),
    val answer: String? = null,
    val truncated: Boolean = false,
)

data class ToolWebSource(
    val url: String,
    val title: String? = null,
    val snippet: String? = null,
    val publishedAt: String? = null,
)
