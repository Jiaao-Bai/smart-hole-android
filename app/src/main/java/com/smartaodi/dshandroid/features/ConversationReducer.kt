package com.smartaodi.dshandroid.features

import com.smartaodi.dshandroid.protocol.ChatMessage
import com.smartaodi.dshandroid.protocol.ChatRole
import com.smartaodi.dshandroid.protocol.DshProtocol
import com.smartaodi.dshandroid.protocol.DshServerEvent
import com.smartaodi.dshandroid.protocol.DshSurfaceAdapter
import com.smartaodi.dshandroid.protocol.SessionProjectionSnapshot
import com.smartaodi.dshandroid.features.reasoning.AssistantDeltaKind
import com.smartaodi.dshandroid.features.reasoning.ReasoningFeatureCodec

data class ConversationState(
    val messages: List<ChatMessage> = emptyList(),
    val projections: SessionProjectionSnapshot? = null,
    val promptSubmitting: Boolean = false,
    val conversationError: String? = null,
    val planMode: Boolean = false,
)

data class ConversationReduction(
    val state: ConversationState,
    val reloadSessionList: Boolean = false,
)

/** Pure replay reducer shared by history/live event semantics. */
object ConversationReducer {
    fun reduce(
        current: ConversationState,
        serverEvent: DshServerEvent,
        selectedSessionId: String?,
    ): ConversationReduction {
        if (serverEvent.payload.optString("sessionId") != selectedSessionId) {
            return ConversationReduction(current)
        }
        if (serverEvent.method == "session/projection") {
            val key = serverEvent.payload.optString("key").takeIf { it.isNotBlank() }
                ?: return ConversationReduction(current)
            val baseline = current.projections ?: SessionProjectionSnapshot(-1L, emptyMap())
            val updated = baseline.updated(
                key = key,
                valueJson = DshSurfaceAdapter.projectionValue(serverEvent.payload.opt("value")),
                seq = serverEvent.payload.optLong("seq", -1L),
            )
            val planMode = WorkFeatureModule.decode(
                SessionFeatureContext(selectedSessionId, updated, emptyList()),
            )?.let { it as SessionFeature.Work }?.plan?.enabled ?: current.planMode
            return ConversationReduction(current.copy(projections = updated, planMode = planMode))
        }
        if (serverEvent.method != "session/event") return ConversationReduction(current)

        val event = serverEvent.payload.optJSONObject("event") ?: return ConversationReduction(current)
        var next = when (event.optString("type")) {
            "turn/start" -> current.copy(promptSubmitting = true, conversationError = null)
            "turn/end" -> current.copy(
                promptSubmitting = false,
                conversationError = DshProtocol.turnError(event),
            )
            "plan/mode" -> current.copy(
                planMode = event.optJSONObject("data")?.optBoolean("active", false) == true,
            )
            else -> current
        }
        val delta = ReasoningFeatureCodec.assistantDelta(event)
        if (delta != null) {
            val data = event.optJSONObject("data")
            val streamId = "stream-${data?.optInt("turn", -1)}-${data?.optInt("step", -1)}"
            val messages = next.messages.toMutableList()
            val existing = messages.indexOfFirst { it.id == streamId }
            if (existing >= 0) {
                val message = messages[existing]
                messages[existing] = when (delta.kind) {
                    AssistantDeltaKind.Text -> message.copy(text = message.text + delta.text)
                    AssistantDeltaKind.Reasoning -> message.copy(reasoning = message.reasoning.orEmpty() + delta.text)
                }
            } else {
                messages += ChatMessage(
                    id = streamId,
                    role = ChatRole.Assistant,
                    text = if (delta.kind == AssistantDeltaKind.Text) delta.text else "",
                    reasoning = delta.text.takeIf { delta.kind == AssistantDeltaKind.Reasoning },
                    streaming = true,
                )
            }
            next = next.copy(messages = messages)
        } else {
            next = next.copy(
                messages = DshProtocol.foldConversationEvent(
                    next.messages,
                    event,
                    serverEvent.payload.optJSONObject("view"),
                ),
            )
        }
        return ConversationReduction(next, reloadSessionList = event.optString("type") == "turn/end")
    }
}
