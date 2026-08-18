package com.smartaodi.dshandroid.features.workspace

import com.smartaodi.dshandroid.protocol.SessionSummary
import org.json.JSONObject

data class WorkspaceView(
    val workspaceId: String,
    val path: String,
    val title: String,
    val sessionIds: List<String>,
    val createdAt: String,
    val updatedAt: String,
)

data class WorkspaceFeatureState(
    val items: List<WorkspaceView> = emptyList(),
    val archivedSessionIds: Set<String> = emptySet(),
    val loading: Boolean = false,
    val operationInProgress: Boolean = false,
    val error: String? = null,
) {
    fun visibleRootSessions(sessions: List<SessionSummary>, selectedSessionId: String?): List<SessionSummary> =
        sessions.filter {
            it.origin != "subagent" &&
                it.sessionId !in archivedSessionIds &&
                (!it.blank || it.sessionId == selectedSessionId)
        }

}

data class WorkspaceDirectory(
    val items: List<WorkspaceView>,
    val archivedSessionIds: Set<String>,
)

/** Wire decoder colocated with the Workspace feature contract. */
object WorkspaceFeatureCodec {
    fun directory(value: Any?): WorkspaceDirectory {
        val body = value as? JSONObject ?: error("workspace.list returned a non-object value")
        val rawItems = body.optJSONArray("items")
        val items = buildList {
            if (rawItems != null) for (index in 0 until rawItems.length()) {
                workspace(rawItems.optJSONObject(index))?.let(::add)
            }
        }
        val archived = body.optJSONArray("archivedSessionIds")
        val archivedIds = buildSet {
            if (archived != null) for (index in 0 until archived.length()) {
                archived.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
        return WorkspaceDirectory(items, archivedIds)
    }

    private fun workspace(item: JSONObject?): WorkspaceView? {
        item ?: return null
        val id = item.optString("workspaceId").takeIf(String::isNotBlank) ?: return null
        val sessionIds = item.optJSONArray("sessionIds")
        return WorkspaceView(
            workspaceId = id,
            path = item.optString("path"),
            title = item.optString("title"),
            sessionIds = buildList {
                if (sessionIds != null) for (index in 0 until sessionIds.length()) {
                    sessionIds.optString(index).takeIf(String::isNotBlank)?.let(::add)
                }
            },
            createdAt = item.optString("createdAt"),
            updatedAt = item.optString("updatedAt"),
        )
    }
}
