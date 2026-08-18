package com.smartaodi.dshandroid.protocol

import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID

object DshProtocol {
    data class RequestEnvelope(
        val rpcId: String,
        val json: String,
    )

    fun request(
        method: String,
        payload: JSONObject = JSONObject(),
        rpcId: String = UUID.randomUUID().toString(),
    ): RequestEnvelope {
        val body = JSONObject()
            .put("type", "client-request")
            .put("rpcId", rpcId)
            .put("method", method)
            .put("payload", payload)
        return RequestEnvelope(rpcId, body.toString())
    }

    fun clientResponse(rpcId: String, value: JSONObject): String = JSONObject()
        .put("type", "client-response")
        .put("rpcId", rpcId)
        .put("result", JSONObject().put("ok", true).put("value", value))
        .toString()

    fun cancelledClientResponse(rpcId: String): String = JSONObject()
        .put("type", "client-response")
        .put("rpcId", rpcId)
        .put(
            "result",
            JSONObject()
                .put("ok", false)
                .put(
                    "error",
                    JSONObject()
                        .put("code", "cancelled")
                        .put("message", "Cancelled by user")
                        .put("details", JSONObject()),
                ),
        )
        .toString()

    fun responseAccepted(body: String): Boolean {
        val root = parseObject(body, "RPC receipt")
        return root.optBoolean("accepted", false)
    }

    fun response(expectedRpcId: String, body: String): DshRpcResult {
        val root = parseObject(body, "RPC response")
        if (root.optString("type") != "server-response") {
            throw DshProtocolException("Expected server-response")
        }
        val actualRpcId = root.optString("rpcId")
        if (actualRpcId != expectedRpcId) {
            throw DshProtocolException("rpcId mismatch: expected $expectedRpcId, got $actualRpcId")
        }
        val result = root.optJSONObject("result")
            ?: throw DshProtocolException("Missing result object")
        return if (result.optBoolean("ok", false)) {
            DshRpcResult.Success(result.opt("value").takeUnless { it === JSONObject.NULL })
        } else {
            val error = result.optJSONObject("error")
                ?: throw DshProtocolException("Missing RPC error")
            DshRpcResult.Failure(
                code = error.optString("code", "internal"),
                message = error.optString("message", "Unknown RPC error"),
                details = error.optJSONObject("details") ?: JSONObject(),
            )
        }
    }

    fun serverEvent(text: String): DshServerEvent {
        val root = parseObject(text, "server event")
        if (root.optString("type") != "server-request") {
            throw DshProtocolException("Expected server-request")
        }
        val rpcId = root.optString("rpcId")
        val method = root.optString("method")
        val payload = root.optJSONObject("payload")
            ?: throw DshProtocolException("Missing event payload")
        if (rpcId.isBlank() || method.isBlank()) {
            throw DshProtocolException("Missing event rpcId or method")
        }
        val payloadType = payload.optString("type")
        if (payloadType.isNotEmpty() && payloadType != method) {
            throw DshProtocolException("Event method does not match payload type")
        }
        return DshServerEvent(rpcId, method, payload)
    }

    fun pendingInteraction(event: DshServerEvent): PendingInteraction? {
        val payload = event.payload
        val sessionId = payload.optString("sessionId").takeIf { it.isNotBlank() } ?: return null
        return when (event.method) {
            "approval/requested" -> PendingInteraction.Approval(
                rpcId = event.rpcId,
                sessionId = sessionId,
                approvalId = payload.optString("approvalId").takeIf { it.isNotBlank() } ?: return null,
                toolName = payload.optString("toolName", "tool"),
                reason = payload.optString("reason").takeIf { it.isNotBlank() },
            )
            "question/requested" -> {
                val items = payload.optJSONArray("questions") ?: return null
                val questions = buildList {
                    for (index in 0 until items.length()) {
                        val item = items.optJSONObject(index) ?: continue
                        val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                        val question = item.optString("question").takeIf { it.isNotBlank() } ?: continue
                        val options = item.optJSONArray("options")?.let { array ->
                            buildList {
                                for (optionIndex in 0 until array.length()) {
                                    val option = array.optJSONObject(optionIndex) ?: continue
                                    val label = option.optString("label").takeIf { it.isNotBlank() } ?: continue
                                    add(
                                        UserQuestionOption(
                                            label = label,
                                            description = option.optString("description").takeIf { it.isNotBlank() },
                                        ),
                                    )
                                }
                            }
                        }.orEmpty()
                        add(
                            UserQuestion(
                                id = id,
                                question = question,
                                header = item.optString("header").takeIf { it.isNotBlank() },
                                detail = item.optString("detail").takeIf { it.isNotBlank() },
                                options = options,
                                multiSelect = item.optBoolean("multiSelect", false),
                            ),
                        )
                    }
                }
                PendingInteraction.Question(event.rpcId, sessionId, questions).takeIf {
                    it.questions.isNotEmpty()
                }
            }
            else -> null
        }
    }

    fun resolvedInteractionId(event: DshServerEvent): String? = when (event.method) {
        "approval/resolved" -> event.payload.optString("approvalId").takeIf { it.isNotBlank() }
        "question/resolved" -> event.payload.optString("questionRpcId").takeIf { it.isNotBlank() }
        else -> null
    }

    fun hostDescription(value: Any?): HostDescription {
        val body = value as? JSONObject
            ?: throw DshProtocolException("host.describe returned a non-object value")
        val host = HostDescription(
            version = body.getString("version"),
            cwd = body.getString("cwd"),
            provider = body.optString("provider").takeIf { it.isNotBlank() },
            model = body.optString("model").takeIf { it.isNotBlank() },
            attachedSessions = body.getInt("attachedSessions"),
            canOpenPath = body.getBoolean("canOpenPath"),
        )
        DshSurfaceAdapter.requireCompatibleHostApiVersion(host.version)
        return host
    }

    fun modelDirectory(value: Any?): ModelDirectory {
        val body = value as? JSONObject
            ?: throw DshProtocolException("session.models returned a non-object value")
        val current = body.optJSONObject("current")?.let(::modelSelection)
        val groups = body.optJSONArray("groups")?.let { items ->
            buildList {
                for (index in 0 until items.length()) {
                    val group = items.optJSONObject(index) ?: continue
                    val id = group.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val models = group.optJSONArray("models")?.let { modelItems ->
                        buildList {
                            for (modelIndex in 0 until modelItems.length()) {
                                val model = modelItems.optJSONObject(modelIndex) ?: continue
                                val modelId = model.optString("id").takeIf { it.isNotBlank() } ?: continue
                                val reasoning = model.optJSONObject("reasoning")
                                val efforts = reasoning?.optJSONArray("efforts")?.let { effortItems ->
                                    buildList {
                                        for (effortIndex in 0 until effortItems.length()) {
                                            val effort = effortItems.optJSONObject(effortIndex) ?: continue
                                            val effortId = effort.optString("id").takeIf { it.isNotBlank() } ?: continue
                                            add(ModelEffort(effortId, effort.optString("name", effortId)))
                                        }
                                    }
                                }.orEmpty()
                                add(
                                    ModelOption(
                                        id = modelId,
                                        name = model.optString("name", modelId),
                                        efforts = efforts,
                                        defaultEffort = reasoning?.optString("defaultEffort")?.takeIf { it.isNotBlank() },
                                    ),
                                )
                            }
                        }
                    }.orEmpty()
                    add(ModelProviderGroup(id, group.optString("name", id), models))
                }
            }
        }.orEmpty()
        return ModelDirectory(current, body.optBoolean("routable", false), groups)
    }

    fun modelSelection(value: Any?): ModelSelection {
        val body = value as? JSONObject
            ?: throw DshProtocolException("model selection returned a non-object value")
        return ModelSelection(
            provider = body.optString("provider").takeIf { it.isNotBlank() }
                ?: throw DshProtocolException("model selection returned no provider"),
            model = body.optString("model").takeIf { it.isNotBlank() }
                ?: throw DshProtocolException("model selection returned no model"),
            reasoningEffort = body.optString("reasoningEffort").takeIf { it.isNotBlank() },
        )
    }

    fun sessionId(value: Any?): String {
        val body = value as? JSONObject
            ?: throw DshProtocolException("session.create returned a non-object value")
        return body.optString("sessionId").takeIf { it.isNotBlank() }
            ?: throw DshProtocolException("session.create returned no sessionId")
    }

    fun latestSessionId(value: Any?, agentPreset: String? = null): String? {
        val body = value as? JSONObject
            ?: throw DshProtocolException("session.list returned a non-object value")
        val items = body.optJSONArray("items") ?: return null
        var latestId: String? = null
        var latestTime = Long.MIN_VALUE
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val id = item.optString("sessionId").takeIf { it.isNotBlank() } ?: continue
            if (agentPreset != null && item.optString("agentPreset") != agentPreset) continue
            val updatedAt = item.optLong("updatedAt", Long.MIN_VALUE)
            if (updatedAt >= latestTime) {
                latestTime = updatedAt
                latestId = id
            }
        }
        return latestId
    }

    fun sessions(value: Any?, agentPreset: String? = null): List<SessionSummary> {
        val body = value as? JSONObject
            ?: throw DshProtocolException("session.list returned a non-object value")
        val items = body.optJSONArray("items") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("sessionId").takeIf { it.isNotBlank() } ?: continue
                if (agentPreset != null && item.optString("agentPreset") != agentPreset) continue
                val title = item.optJSONObject("projections")
                    ?.optJSONObject("values")
                    ?.optString("title")
                    ?.takeIf { it.isNotBlank() && it != "null" }
                val plan = item.optJSONObject("projections")
                    ?.optJSONObject("values")
                    ?.optJSONObject("plan")
                val planMode = plan?.let {
                    if (it.optBoolean("pending", false)) !it.optBoolean("active", false)
                    else it.optBoolean("active", false)
                } ?: false
                val projections = DshSurfaceAdapter.projectionSnapshot(item.optJSONObject("projections"))
                add(
                    SessionSummary(
                        sessionId = id,
                        updatedAt = item.optLong("updatedAt", 0L),
                        running = item.optBoolean("running", false),
                        blank = item.optBoolean("blank", false),
                        title = title,
                        cwd = item.optString("cwd").takeIf { it.isNotBlank() },
                        planMode = planMode,
                        parentSessionId = item.optString("parentSessionId").takeIf { it.isNotBlank() },
                        origin = item.optString("origin").takeIf { it.isNotBlank() },
                        projections = projections,
                    ),
                )
            }
        }.sortedByDescending(SessionSummary::updatedAt)
    }

    fun history(value: Any?): SessionHistory {
        val body = value as? JSONObject
            ?: throw DshProtocolException("session.history returned a non-object value")
        val events = body.optJSONArray("events") ?: JSONArray()
        var messages = emptyList<ChatMessage>()
        var oldestSeq: Long? = null
        for (index in 0 until events.length()) {
            val entry = events.optJSONObject(index) ?: continue
            val event = entry.optJSONObject("event") ?: continue
            val seq = event.optLong("seq", -1L)
            if (seq >= 0L && (oldestSeq == null || seq < oldestSeq)) oldestSeq = seq
            messages = foldConversationEvent(messages, event, entry.optJSONObject("view"))
        }
        return SessionHistory(
            messages = messages,
            hasMore = body.optBoolean("hasMore", false),
            oldestSeq = oldestSeq,
            projections = DshSurfaceAdapter.projectionSnapshot(body.optJSONObject("projections")),
        )
    }

    fun foldConversationEvent(
        messages: List<ChatMessage>,
        event: JSONObject,
        eventView: JSONObject? = null,
    ): List<ChatMessage> {
        return when (event.optString("type")) {
            "tool/call" -> foldToolCall(messages, event, eventView)
            "tool/result" -> foldToolResult(messages, event, eventView)
            "turn/end" -> finishToolActivity(messages, event.optJSONObject("data")?.optInt("turn", -1) ?: -1)
            else -> {
                val message = finalizedMessage(event) ?: return messages
                val withoutDraft = if (message.role == ChatRole.Assistant) {
                    messages.filterNot { it.role == ChatRole.Assistant && it.streaming }
                } else {
                    messages
                }
                if (withoutDraft.any { it.id == message.id }) withoutDraft else withoutDraft + message
            }
        }
    }

    fun finalizedMessage(event: JSONObject): ChatMessage? {
        val seq = event.optLong("seq", -1)
        val data = event.optJSONObject("data") ?: return null
        return when (event.optString("type")) {
            "user/message" -> visibleText(data.optJSONArray("content"))
                .takeIf { isVisibleUserMessage(data, it) }
                ?.let { ChatMessage(id = "event-$seq", role = ChatRole.User, text = it) }
            "assistant/message" -> {
                val content = data.optJSONObject("message")?.optJSONArray("content")
                val text = visibleText(content)
                val reasoning = visibleReasoning(content)
                if (text.isBlank() && reasoning.isBlank()) null else ChatMessage(
                    id = "event-$seq",
                    role = ChatRole.Assistant,
                    text = text,
                    reasoning = reasoning.takeIf(String::isNotBlank),
                )
            }
            else -> null
        }
    }

    fun turnError(event: JSONObject): String? {
        if (event.optString("type") != "turn/end") return null
        val reason = event.optJSONObject("data")?.optJSONObject("reason") ?: return null
        if (reason.optString("kind") != "error") return null
        return reason.optJSONObject("error")
            ?.optString("message")
            ?.takeIf { it.isNotBlank() }
            ?: "Harness turn failed"
    }

    private fun visibleText(content: JSONArray?): String {
        if (content == null) return ""
        return buildList {
            for (index in 0 until content.length()) {
                val block = content.optJSONObject(index) ?: continue
                if (block.optString("type") == "text") {
                    block.optString("text").takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.joinToString("\n\n")
    }

    private fun visibleReasoning(content: JSONArray?): String {
        if (content == null) return ""
        return buildList {
            for (index in 0 until content.length()) {
                val block = content.optJSONObject(index) ?: continue
                if (block.optString("type") == "reasoning") {
                    block.optString("text").takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }.joinToString("\n\n")
    }

    private fun foldToolCall(
        messages: List<ChatMessage>,
        event: JSONObject,
        eventView: JSONObject?,
    ): List<ChatMessage> {
        val data = event.optJSONObject("data") ?: return messages
        val turn = data.optInt("turn", -1)
        val callId = data.optString("callId").takeIf { it.isNotBlank() } ?: return messages
        val name = data.optString("name", "tool")
        val arguments = data.optString("arguments")
        val presentation = DshSurfaceAdapter.toolPresentation(eventView, "call")
        val call = ToolCallTrace(
            callId = callId,
            name = name,
            summary = presentation?.description
                ?: presentation?.title
                ?: toolSummary(name, arguments, callId),
            input = prettyJson(arguments),
            callPresentation = presentation,
        )
        return updateToolActivity(messages, turn) { activity ->
            val index = activity.calls.indexOfFirst { it.callId == callId }
            val calls = activity.calls.toMutableList()
            if (index >= 0) {
                val previous = calls[index]
                calls[index] = call.copy(
                    output = previous.output,
                    state = previous.state,
                    callPresentation = presentation ?: previous.callPresentation,
                    resultPresentation = previous.resultPresentation,
                )
            } else {
                calls += call
            }
            activity.copy(calls = calls, running = activity.running || index < 0)
        }
    }

    private fun foldToolResult(
        messages: List<ChatMessage>,
        event: JSONObject,
        eventView: JSONObject?,
    ): List<ChatMessage> {
        val data = event.optJSONObject("data") ?: return messages
        val message = data.optJSONObject("message") ?: return messages
        val callId = message.optJSONObject("source")
            ?.optString("callId")
            ?.takeIf { it.isNotBlank() }
            ?: return messages
        val turn = data.optInt("turn", -1)
        val result = message.optJSONArray("content")?.optJSONObject(0)
        val output = resultContent(result)
        val presentation = DshSurfaceAdapter.toolPresentation(eventView, "result")
        val interrupted = data.optJSONObject("error")?.optString("code") == "interrupted"
        val state = when {
            interrupted -> ToolCallState.Stopped
            result?.optBoolean("isError", false) == true -> ToolCallState.Error
            else -> ToolCallState.Success
        }
        return updateToolActivity(messages, turn) { activity ->
            val index = activity.calls.indexOfFirst { it.callId == callId }
            val calls = activity.calls.toMutableList()
            if (index >= 0) {
                calls[index] = calls[index].copy(
                    output = output,
                    state = state,
                    resultPresentation = presentation,
                )
            } else {
                calls += ToolCallTrace(
                    callId = callId,
                    name = "tool",
                    summary = callId,
                    input = "",
                    output = output,
                    state = state,
                    resultPresentation = presentation,
                )
            }
            activity.copy(calls = calls)
        }
    }

    private fun finishToolActivity(messages: List<ChatMessage>, turn: Int): List<ChatMessage> {
        if (turn < 0) return messages
        val index = messages.indexOfFirst { it.toolActivity?.turn == turn }
        if (index < 0) return messages
        val activity = messages[index].toolActivity ?: return messages
        val settled = activity.copy(
            running = false,
            calls = activity.calls.map { call ->
                if (call.state == ToolCallState.Running) call.copy(state = ToolCallState.Stopped) else call
            },
        )
        return messages.toMutableList().also { it[index] = it[index].copy(toolActivity = settled) }
    }

    private fun updateToolActivity(
        messages: List<ChatMessage>,
        turn: Int,
        update: (ToolActivity) -> ToolActivity,
    ): List<ChatMessage> {
        val id = "tools-turn-$turn"
        val index = messages.indexOfFirst { it.id == id }
        val current = if (index >= 0) {
            messages[index].toolActivity ?: ToolActivity(turn, emptyList(), running = true)
        } else {
            ToolActivity(turn, emptyList(), running = true)
        }
        val message = ChatMessage(
            id = id,
            role = ChatRole.Tool,
            text = "",
            toolActivity = update(current),
        )
        if (index < 0) return messages + message
        return messages.toMutableList().also { it[index] = message }
    }

    private fun resultContent(result: JSONObject?): String? {
        if (result == null) return null
        val content = result.optJSONArray("content") ?: return null
        return buildList {
            for (index in 0 until content.length()) {
                val block = content.optJSONObject(index) ?: continue
                if (block.optString("type") == "text") add(block.optString("text"))
                else add(block.toString(2))
            }
        }.joinToString("\n").takeIf { it.isNotBlank() }
    }

    private fun toolSummary(name: String, arguments: String, fallback: String): String {
        val parsed = runCatching { JSONObject(arguments) }.getOrNull() ?: return arguments.lineSequence()
            .firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: fallback
        val preferredKeys = when (name) {
            "bash", "pwsh" -> listOf("description", "command")
            "read", "write", "edit" -> listOf("path", "file_path")
            "web_search" -> listOf("query")
            "web_fetch" -> listOf("url")
            "android_system" -> listOf("operation")
            else -> listOf("description", "command", "path", "file_path", "query", "pattern", "url", "operation")
        }
        for (key in preferredKeys) {
            parsed.optString(key).takeIf { it.isNotBlank() }?.let { return it.lineSequence().first() }
        }
        for (key in parsed.keys()) {
            parsed.optString(key).takeIf { it.isNotBlank() }?.let { return it.lineSequence().first() }
        }
        return fallback
    }

    private fun prettyJson(value: String): String {
        if (value.isBlank()) return ""
        return runCatching { JSONObject(value).toString(2) }
            .recoverCatching { JSONArray(value).toString(2) }
            .getOrDefault(value)
    }

    private fun isVisibleUserMessage(data: JSONObject, text: String): Boolean {
        val sourceKind = data.optJSONObject("source")
            ?.optString("kind")
            ?.takeIf { it.isNotBlank() }
        val directOrLegacy = sourceKind == null || sourceKind == "user"
        return directOrLegacy && INTERNAL_CONTEXT_PREFIXES.none(text::startsWith)
    }

    private fun parseObject(text: String, label: String): JSONObject = try {
        JSONObject(text)
    } catch (error: Exception) {
        throw DshProtocolException("Invalid $label JSON: ${error.message}")
    }

    private val INTERNAL_CONTEXT_PREFIXES = listOf(
        "Current runtime context. This snapshot supersedes earlier runtime-context snapshots.",
        "Current DSH file policy:",
    )
}
