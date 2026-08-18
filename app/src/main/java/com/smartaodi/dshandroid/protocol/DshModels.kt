package com.smartaodi.dshandroid.protocol

import org.json.JSONObject

data class HostDescription(
    val version: String,
    val cwd: String,
    val provider: String?,
    val model: String?,
    val attachedSessions: Int,
    val canOpenPath: Boolean,
)

data class DshServerEvent(
    val rpcId: String,
    val method: String,
    val payload: JSONObject,
)

enum class ChatRole {
    User,
    Assistant,
    Tool,
}

enum class ToolCallState {
    Running,
    Success,
    Error,
    Stopped,
}

data class ToolCallTrace(
    val callId: String,
    val name: String,
    val summary: String,
    val input: String,
    val output: String? = null,
    val state: ToolCallState = ToolCallState.Running,
    val callPresentation: ToolPresentation? = null,
    val resultPresentation: ToolPresentation? = null,
)

data class ToolActivity(
    val turn: Int,
    val calls: List<ToolCallTrace>,
    val running: Boolean,
)

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val reasoning: String? = null,
    val streaming: Boolean = false,
    val toolActivity: ToolActivity? = null,
)

data class SessionHistory(
    val messages: List<ChatMessage>,
    val hasMore: Boolean,
    val oldestSeq: Long? = null,
    val projections: SessionProjectionSnapshot? = null,
)

data class SessionSummary(
    val sessionId: String,
    val updatedAt: Long,
    val running: Boolean,
    val blank: Boolean,
    val title: String?,
    val cwd: String?,
    val planMode: Boolean,
    val parentSessionId: String? = null,
    val origin: String? = null,
    val projections: SessionProjectionSnapshot? = null,
)

sealed interface PendingInteraction {
    val rpcId: String
    val sessionId: String

    data class Approval(
        override val rpcId: String,
        override val sessionId: String,
        val approvalId: String,
        val toolName: String,
        val reason: String?,
    ) : PendingInteraction

    data class Question(
        override val rpcId: String,
        override val sessionId: String,
        val questions: List<UserQuestion>,
    ) : PendingInteraction
}

data class UserQuestion(
    val id: String,
    val question: String,
    val header: String?,
    val detail: String?,
    val options: List<UserQuestionOption>,
    val multiSelect: Boolean,
)

data class UserQuestionOption(
    val label: String,
    val description: String?,
)

data class UserQuestionAnswer(
    val id: String,
    val selected: List<String>,
    val custom: String? = null,
)

data class ModelSelection(
    val provider: String,
    val model: String,
    val reasoningEffort: String? = null,
)

data class ModelDirectory(
    val current: ModelSelection?,
    val routable: Boolean,
    val groups: List<ModelProviderGroup>,
)

data class ModelProviderGroup(
    val id: String,
    val name: String,
    val models: List<ModelOption>,
)

data class ModelOption(
    val id: String,
    val name: String,
    val efforts: List<ModelEffort>,
    val defaultEffort: String?,
)

data class ModelEffort(
    val id: String,
    val name: String,
)

sealed interface DshRpcResult {
    data class Success(val value: Any?) : DshRpcResult

    data class Failure(
        val code: String,
        val message: String,
        val details: JSONObject,
    ) : DshRpcResult
}

class DshProtocolException(message: String) : Exception(message)

class DshRpcException(
    val code: String,
    message: String,
) : Exception(message)
