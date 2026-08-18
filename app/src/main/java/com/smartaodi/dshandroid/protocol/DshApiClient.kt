package com.smartaodi.dshandroid.protocol

import com.smartaodi.dshandroid.features.queue.PromptMode
import com.smartaodi.dshandroid.features.queue.QueueFeatureCodec
import com.smartaodi.dshandroid.features.commands.CommandDescriptor
import com.smartaodi.dshandroid.features.commands.CommandsFeatureCodec
import com.smartaodi.dshandroid.features.workspace.WorkspaceDirectory
import com.smartaodi.dshandroid.features.workspace.WorkspaceFeatureCodec
import com.smartaodi.dshandroid.features.status.ApiBalance
import com.smartaodi.dshandroid.features.status.ConversationStatusCodec
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DshApiClient(
    private val baseUrl: String = "http://127.0.0.1:3080",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    enum class Stream(val path: String) {
        Host("/api/events.host"),
        Mux("/api/events.mux"),
    }

    interface EventListener {
        fun onOpen(stream: Stream)
        fun onEvent(stream: Stream, event: DshServerEvent)
        fun onFailure(stream: Stream, error: Throwable)
        fun onClosed(stream: Stream)
    }

    suspend fun hostDescription(): HostDescription {
        return when (val result = call("host.describe")) {
            is DshRpcResult.Success -> DshProtocol.hostDescription(result.value)
            is DshRpcResult.Failure -> throw DshRpcException(result.code, result.message)
        }
    }

    suspend fun createSession(cwd: String? = null, workspaceId: String? = null): String {
        val payload = JSONObject()
            .put("agentPreset", ANDROID_AGENT_PRESET)
            .apply { cwd?.let { put("cwd", it) } }
            .apply { workspaceId?.let { put("workspaceId", it) } }
        return DshProtocol.sessionId(successValue("session.create", payload))
    }

    suspend fun latestSessionId(): String? = DshProtocol.latestSessionId(
        successValue("session.list", JSONObject()),
        agentPreset = ANDROID_AGENT_PRESET,
    )

    suspend fun sessions(): List<SessionSummary> = DshProtocol.sessions(
        successValue("session.list", JSONObject()),
        agentPreset = ANDROID_AGENT_PRESET,
    )

    suspend fun sessionHistory(
        sessionId: String,
        maxMessages: Int = 100,
        beforeSeq: Long? = null,
    ): SessionHistory {
        val payload = JSONObject()
            .put("sessionId", sessionId)
            .put("maxMessages", maxMessages)
            .apply { beforeSeq?.let { put("beforeSeq", it) } }
        return DshProtocol.history(successValue("session.history", payload))
    }

    suspend fun workspaces(): WorkspaceDirectory = WorkspaceFeatureCodec.directory(
        successValue("workspace.list", JSONObject()),
    )

    suspend fun renameWorkspace(workspaceId: String, title: String) {
        successValue(
            "workspace.rename",
            JSONObject().put("workspaceId", workspaceId).put("title", title),
        )
    }

    suspend fun deleteWorkspace(workspaceId: String) {
        successValue("workspace.delete", JSONObject().put("workspaceId", workspaceId))
    }

    suspend fun archiveSession(sessionId: String) {
        successValue("workspace.archiveSession", JSONObject().put("sessionId", sessionId))
    }

    suspend fun renameSession(sessionId: String, title: String) {
        successValue(
            "session.rename",
            JSONObject().put("sessionId", sessionId).put("title", title),
        )
    }

    suspend fun forkSession(sessionId: String): String = DshProtocol.sessionId(
        successValue("session.fork", JSONObject().put("sessionId", sessionId)),
    )

    suspend fun sessionModels(sessionId: String): ModelDirectory = DshProtocol.modelDirectory(
        successValue("session.models", JSONObject().put("sessionId", sessionId)),
    )

    suspend fun selectModel(sessionId: String, selection: ModelSelection): ModelSelection {
        val payload = JSONObject()
            .put("sessionId", sessionId)
            .put("provider", selection.provider)
            .put("model", selection.model)
            .apply { selection.reasoningEffort?.let { put("reasoningEffort", it) } }
        val value = successValue("session.selectModel", payload) as? JSONObject
            ?: throw DshProtocolException("session.selectModel returned a non-object value")
        return DshProtocol.modelSelection(value.optJSONObject("selected"))
    }

    suspend fun executeCommand(sessionId: String, line: String) {
        val value = successValue(
            "commands/execute",
            JSONObject().put(
                "args",
                JSONObject().put("agentId", sessionId).put("line", line),
            ),
        )
        if (value == null) throw DshProtocolException("Harness does not recognize $line")
        val result = (value as? JSONObject)?.optJSONObject("result")
        if (result?.optString("kind") == "error") {
            throw DshProtocolException(result.optString("text", "Command failed"))
        }
    }

    suspend fun commands(sessionId: String): List<CommandDescriptor> = CommandsFeatureCodec.directory(
        successValue(
            "commands/list",
            JSONObject().put("args", JSONObject().put("agentId", sessionId)),
        ),
    )

    suspend fun prompt(sessionId: String, text: String, mode: PromptMode) {
        val payload = JSONObject()
            .put("sessionId", sessionId)
            .put("mode", mode.wireValue)
            .put("clientTimeZone", java.util.TimeZone.getDefault().id)
            .put("content", QueueFeatureCodec.textContent(text))
        successValue("session.prompt", payload)
    }

    suspend fun editQueueItem(sessionId: String, itemId: String, text: String) {
        updateQueue(
            sessionId,
            itemId,
            JSONObject().put("kind", "edit").put("content", QueueFeatureCodec.textContent(text)),
        )
    }

    suspend fun removeQueueItem(sessionId: String, itemId: String) {
        updateQueue(sessionId, itemId, JSONObject().put("kind", "remove"))
    }

    suspend fun steerQueueItem(sessionId: String, itemId: String) {
        updateQueue(sessionId, itemId, JSONObject().put("kind", "steer"))
    }

    private suspend fun updateQueue(sessionId: String, itemId: String, action: JSONObject) {
        successValue(
            "session.updateQueue",
            JSONObject().put("sessionId", sessionId).put("itemId", itemId).put("action", action),
        )
    }

    suspend fun cancel(sessionId: String) {
        successValue("session.cancel", JSONObject().put("sessionId", sessionId))
    }

    suspend fun answerApproval(
        rpcId: String,
        sessionId: String,
        approvalId: String,
        outcome: String,
    ) {
        require(outcome == "allowed-once" || outcome == "rejected")
        respond(
            DshProtocol.clientResponse(
                rpcId,
                JSONObject()
                    .put("sessionId", sessionId)
                    .put("approvalId", approvalId)
                    .put("outcome", outcome),
            ),
        )
    }

    suspend fun answerQuestions(
        rpcId: String,
        sessionId: String,
        answers: List<UserQuestionAnswer>,
    ) {
        val encodedAnswers = JSONArray()
        answers.forEach { answer ->
            encodedAnswers.put(
                JSONObject()
                    .put("id", answer.id)
                    .put("selected", JSONArray(answer.selected))
                    .apply { answer.custom?.takeIf { it.isNotBlank() }?.let { put("custom", it) } },
            )
        }
        respond(
            DshProtocol.clientResponse(
                rpcId,
                JSONObject()
                    .put("sessionId", sessionId)
                    .put("answer", JSONObject().put("answers", encodedAnswers)),
            ),
        )
    }

    suspend fun cancelQuestions(rpcId: String) {
        respond(DshProtocol.cancelledClientResponse(rpcId))
    }

    suspend fun credentialConfigured(ref: String): Boolean {
        val value = successValue(
            "credentials.describe",
            JSONObject().put("refs", org.json.JSONArray().put(ref)),
        ) as? JSONObject ?: return false
        return value.optJSONObject("credentials")
            ?.optJSONObject(ref)
            ?.optBoolean("configured", false) == true
    }

    suspend fun deepSeekBalance(): ApiBalance? = ConversationStatusCodec.balance(
        successValue(
            "smartHoleStatus/balance",
            JSONObject().put("args", JSONObject()),
        ),
    )

    suspend fun setCredential(ref: String, value: String) {
        successValue(
            "credentials.set",
            JSONObject().put("ref", ref).put("value", value),
        )
    }

    suspend fun call(
        method: String,
        payload: JSONObject = JSONObject(),
    ): DshRpcResult {
        val envelope = DshProtocol.request(method, payload)
        val request = Request.Builder()
            .url("$baseUrl/api/$method")
            .post(envelope.json.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val body = execute(request)
        return DshProtocol.response(envelope.rpcId, body)
    }

    fun open(stream: Stream, listener: EventListener): WebSocket {
        val socketUrl = baseUrl
            .replaceFirst("http://", "ws://")
            .replaceFirst("https://", "wss://") + stream.path
        val request = Request.Builder().url(socketUrl).build()
        return client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                listener.onOpen(stream)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    listener.onEvent(stream, DshProtocol.serverEvent(text))
                } catch (error: Throwable) {
                    listener.onFailure(stream, error)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onFailure(stream, t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onClosed(stream)
            }
        })
    }

    fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private suspend fun successValue(method: String, payload: JSONObject): Any? {
        return when (val result = call(method, payload)) {
            is DshRpcResult.Success -> result.value
            is DshRpcResult.Failure -> throw DshRpcException(result.code, result.message)
        }
    }

    private suspend fun respond(json: String) {
        val request = Request.Builder()
            .url("$baseUrl/api/respond")
            .post(json.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        if (!DshProtocol.responseAccepted(execute(request))) {
            throw DshProtocolException("Harness rejected the interaction response")
        }
    }

    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!continuation.isActive) return
                    if (!response.isSuccessful) {
                        continuation.resumeWithException(
                            IOException("HTTP ${response.code} for ${request.url.encodedPath}"),
                        )
                        return
                    }
                    val body = response.body.string()
                    continuation.resume(body)
                }
            }
        })
    }

    companion object {
        private const val ANDROID_AGENT_PRESET = "android"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
