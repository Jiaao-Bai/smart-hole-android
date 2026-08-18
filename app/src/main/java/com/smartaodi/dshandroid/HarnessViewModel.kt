package com.smartaodi.dshandroid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartaodi.dshandroid.features.ConversationReducer
import com.smartaodi.dshandroid.features.ConversationState
import com.smartaodi.dshandroid.features.CoreSessionFeatureRegistry
import com.smartaodi.dshandroid.features.CoreSessionFeatures
import com.smartaodi.dshandroid.features.SessionFeatureContext
import com.smartaodi.dshandroid.features.history.HistoryFeature
import com.smartaodi.dshandroid.features.history.HistoryFeatureState
import com.smartaodi.dshandroid.features.commands.CommandsFeatureState
import com.smartaodi.dshandroid.features.jobs.JobsFeatureCodec
import com.smartaodi.dshandroid.features.jobs.JobsFeatureState
import com.smartaodi.dshandroid.features.queue.PromptMode
import com.smartaodi.dshandroid.features.queue.QueueFeatureCodec
import com.smartaodi.dshandroid.features.queue.QueueFeatureState
import com.smartaodi.dshandroid.features.workspace.WorkspaceFeatureState
import com.smartaodi.dshandroid.features.status.ApiBalance
import com.smartaodi.dshandroid.protocol.DshApiClient
import com.smartaodi.dshandroid.protocol.ChatMessage
import com.smartaodi.dshandroid.protocol.DshProtocol
import com.smartaodi.dshandroid.protocol.DshProtocolException
import com.smartaodi.dshandroid.protocol.DshServerEvent
import com.smartaodi.dshandroid.protocol.DshSurfaceAdapter
import com.smartaodi.dshandroid.protocol.HostDescription
import com.smartaodi.dshandroid.protocol.ModelDirectory
import com.smartaodi.dshandroid.protocol.ModelSelection
import com.smartaodi.dshandroid.protocol.PendingInteraction
import com.smartaodi.dshandroid.protocol.SessionSummary
import com.smartaodi.dshandroid.protocol.SessionProjectionSnapshot
import com.smartaodi.dshandroid.protocol.UserQuestionAnswer
import com.smartaodi.dshandroid.runtime.RuntimeProbe
import com.smartaodi.dshandroid.runtime.RuntimeProbeRepository
import com.smartaodi.dshandroid.runtime.RuntimeController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.WebSocket

data class HarnessUiState(
    val loading: Boolean = true,
    val runtime: RuntimeProbe = RuntimeProbe.loading(),
    val host: HostDescription? = null,
    val hostError: String? = null,
    val eventStreamsOpen: Set<DshApiClient.Stream> = emptySet(),
    val recentEvents: List<DshServerEvent> = emptyList(),
    val runtimeActionInProgress: Boolean = false,
    val runtimeActionMessage: String? = null,
    val sessionId: String? = null,
    val sessions: List<SessionSummary> = emptyList(),
    val sessionLoading: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val history: HistoryFeatureState = HistoryFeatureState(),
    val queue: QueueFeatureState = QueueFeatureState(),
    val workspaces: WorkspaceFeatureState = WorkspaceFeatureState(),
    val commands: CommandsFeatureState = CommandsFeatureState(),
    val jobs: JobsFeatureState = JobsFeatureState(),
    val promptSubmitting: Boolean = false,
    val conversationError: String? = null,
    val pendingInteractions: List<PendingInteraction> = emptyList(),
    val interactionResponsesInProgress: Set<String> = emptySet(),
    val deepSeekCredentialConfigured: Boolean = false,
    val apiBalance: ApiBalance? = null,
    val credentialSaving: Boolean = false,
    val credentialMessage: String? = null,
    val modelDirectory: ModelDirectory? = null,
    val modelSelectionInProgress: Boolean = false,
    val modelMessage: String? = null,
    val planMode: Boolean = false,
    val planModeChanging: Boolean = false,
    val projectionSnapshot: SessionProjectionSnapshot? = null,
) {
    val coreFeatures: CoreSessionFeatures
        get() = CoreSessionFeatureRegistry.decode(
            SessionFeatureContext(sessionId, projectionSnapshot, sessions),
        )
}

class HarnessViewModel(
    private val runtimeProbe: RuntimeProbeRepository = RuntimeProbeRepository(),
    private val runtimeController: RuntimeController = RuntimeController(),
    private val api: DshApiClient = DshApiClient(),
) : ViewModel(), DshApiClient.EventListener {
    private val _state = MutableStateFlow(HarnessUiState())
    val state: StateFlow<HarnessUiState> = _state.asStateFlow()

    private val sockets = mutableMapOf<DshApiClient.Stream, WebSocket>()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            reload(clearActionMessage = true)
        }
    }

    fun startHost() = runtimeAction(waitForHost = true, runtimeController::start)

    fun stopHost() = runtimeAction(waitForHost = false, runtimeController::stop)

    fun newSession() {
        if (_state.value.sessionLoading || _state.value.host == null) return
        val reusable = _state.value.sessions.firstOrNull { it.blank && it.origin != "subagent" }
        if (reusable != null) {
            openSession(reusable.sessionId)
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(sessionLoading = true, conversationError = null) }
            runCatching { api.createSession() }
                .onSuccess { sessionId ->
                    _state.update {
                        it.copy(
                            sessionId = sessionId,
                            messages = emptyList(),
                            history = HistoryFeatureState(),
                            queue = QueueFeatureState(),
                            commands = CommandsFeatureState(),
                            jobs = JobsFeatureState(),
                            sessionLoading = false,
                            promptSubmitting = false,
                            planMode = false,
                            projectionSnapshot = null,
                        )
                    }
                    reloadModels(sessionId)
                    reloadCommands(sessionId)
                    reloadSessionList()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(sessionLoading = false, conversationError = error.message)
                    }
                }
        }
    }

    fun openSession(sessionId: String) {
        if (_state.value.sessionLoading || _state.value.host == null) return
        if (sessionId == _state.value.sessionId) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    sessionLoading = true,
                    conversationError = null,
                    queue = QueueFeatureState(),
                    commands = CommandsFeatureState(loading = true),
                    jobs = JobsFeatureState(),
                )
            }
            runCatching { api.sessionHistory(sessionId) }
                .onSuccess { history ->
                    val replacement = HistoryFeature.replace(history)
                    _state.update {
                        it.copy(
                            sessionId = sessionId,
                            messages = replacement.messages,
                            history = replacement.state,
                            sessionLoading = false,
                            promptSubmitting = it.sessions.firstOrNull { session ->
                                session.sessionId == sessionId
                            }?.running == true,
                            planMode = it.sessions.firstOrNull { session -> session.sessionId == sessionId }?.planMode == true,
                            projectionSnapshot = history.projections,
                        )
                    }
                    reloadModels(sessionId)
                    reloadCommands(sessionId)
                }
                .onFailure { error ->
                    _state.update { it.copy(sessionLoading = false, conversationError = error.message) }
                }
        }
    }

    fun sendPrompt(text: String, mode: PromptMode = PromptMode.Queue) {
        val prompt = text.trim()
        if (prompt.isEmpty() || _state.value.sessionLoading || _state.value.host == null) return
        val wasRunning = _state.value.promptSubmitting
        viewModelScope.launch {
            _state.update { it.copy(promptSubmitting = true, conversationError = null) }
            runCatching {
                val sessionId = _state.value.sessionId ?: api.createSession().also { created ->
                    _state.update { it.copy(sessionId = created, messages = emptyList()) }
                }
                api.prompt(sessionId, prompt, mode)
            }.onFailure { error ->
                _state.update {
                    it.copy(promptSubmitting = wasRunning, conversationError = error.message)
                }
            }
        }
    }

    fun loadOlderHistory() {
        val current = _state.value
        val sessionId = current.sessionId ?: return
        val beforeSeq = current.history.oldestSeq ?: return
        if (!current.history.hasMore || current.history.loadingOlder) return
        viewModelScope.launch {
            _state.update { it.copy(history = it.history.copy(loadingOlder = true, error = null)) }
            runCatching { api.sessionHistory(sessionId, beforeSeq = beforeSeq) }
                .onSuccess { page ->
                    _state.update { state ->
                        if (state.sessionId != sessionId) return@update state
                        val merged = HistoryFeature.prepend(state.messages, page)
                        state.copy(messages = merged.messages, history = merged.state)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(history = it.history.copy(loadingOlder = false, error = error.message))
                    }
                }
        }
    }

    fun editQueueItem(itemId: String, text: String) = queueOperation(itemId) { sessionId ->
        api.editQueueItem(sessionId, itemId, text.trim())
    }

    fun removeQueueItem(itemId: String) = queueOperation(itemId) { sessionId ->
        api.removeQueueItem(sessionId, itemId)
    }

    fun steerQueueItem(itemId: String) = queueOperation(itemId) { sessionId ->
        api.steerQueueItem(sessionId, itemId)
    }

    fun renameWorkspace(workspaceId: String, title: String) = workspaceOperation {
        api.renameWorkspace(workspaceId, title.trim())
    }

    fun deleteWorkspace(workspaceId: String) = workspaceOperation {
        api.deleteWorkspace(workspaceId)
    }

    fun newSessionInWorkspace(workspaceId: String) {
        if (_state.value.sessionLoading || _state.value.host == null) return
        viewModelScope.launch {
            _state.update { it.copy(sessionLoading = true, conversationError = null) }
            runCatching { api.createSession(workspaceId = workspaceId) }
                .onSuccess { sessionId ->
                    _state.update {
                        it.copy(
                            sessionId = sessionId,
                            messages = emptyList(),
                            history = HistoryFeatureState(),
                            queue = QueueFeatureState(),
                            commands = CommandsFeatureState(),
                            jobs = JobsFeatureState(),
                            sessionLoading = false,
                            promptSubmitting = false,
                            planMode = false,
                            projectionSnapshot = null,
                        )
                    }
                    reloadModels(sessionId)
                    reloadCommands(sessionId)
                    reloadWorkspaceSurface()
                }
                .onFailure { error ->
                    _state.update { it.copy(sessionLoading = false, conversationError = error.message) }
                }
        }
    }

    fun renameSession(sessionId: String, title: String) = workspaceOperation {
        api.renameSession(sessionId, title.trim())
    }

    fun forkSession(sessionId: String) {
        if (_state.value.workspaces.operationInProgress) return
        viewModelScope.launch {
            _state.update { it.copy(workspaces = it.workspaces.copy(operationInProgress = true, error = null)) }
            runCatching { api.forkSession(sessionId) }
                .onSuccess { childId ->
                    reloadWorkspaceSurface()
                    _state.update { it.copy(workspaces = it.workspaces.copy(operationInProgress = false)) }
                    openSession(childId)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(workspaces = it.workspaces.copy(operationInProgress = false, error = error.message))
                    }
                }
        }
    }

    fun archiveSession(sessionId: String) = workspaceOperation {
        api.archiveSession(sessionId)
        if (_state.value.sessionId == sessionId) {
            _state.update {
                it.copy(
                    sessionId = null,
                    messages = emptyList(),
                    history = HistoryFeatureState(),
                    queue = QueueFeatureState(),
                    commands = CommandsFeatureState(),
                    jobs = JobsFeatureState(),
                    promptSubmitting = false,
                    projectionSnapshot = null,
                )
            }
        }
    }

    fun cancelTurn() {
        val sessionId = _state.value.sessionId ?: return
        viewModelScope.launch {
            runCatching { api.cancel(sessionId) }
                .onFailure { error -> _state.update { it.copy(conversationError = error.message) } }
        }
    }

    fun selectModel(selection: ModelSelection) {
        val sessionId = _state.value.sessionId ?: return
        if (_state.value.modelSelectionInProgress || _state.value.promptSubmitting) return
        viewModelScope.launch {
            _state.update { it.copy(modelSelectionInProgress = true, modelMessage = null) }
            runCatching { api.selectModel(sessionId, selection) }
                .onSuccess { selected ->
                    _state.update { current ->
                        current.copy(
                            modelSelectionInProgress = false,
                            modelMessage = "下一次请求将使用 ${selected.model}",
                            modelDirectory = current.modelDirectory?.copy(current = selected),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(modelSelectionInProgress = false, modelMessage = error.message) }
                }
        }
    }

    fun setPlanMode(enabled: Boolean) {
        val sessionId = _state.value.sessionId ?: return
        if (_state.value.planModeChanging) return
        viewModelScope.launch {
            _state.update { it.copy(planModeChanging = true, conversationError = null) }
            runCatching { api.executeCommand(sessionId, if (enabled) "/plan" else "/plan off") }
                .onSuccess {
                    _state.update { it.copy(planMode = enabled, planModeChanging = false) }
                    reloadSessionList()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(planModeChanging = false, conversationError = error.message)
                    }
                }
        }
    }

    fun answerApproval(interaction: PendingInteraction.Approval, allow: Boolean) {
        if (interaction.rpcId in _state.value.interactionResponsesInProgress) return
        respondToInteraction(interaction.rpcId) {
            api.answerApproval(
                rpcId = interaction.rpcId,
                sessionId = interaction.sessionId,
                approvalId = interaction.approvalId,
                outcome = if (allow) "allowed-once" else "rejected",
            )
        }
    }

    fun answerQuestions(interaction: PendingInteraction.Question, answers: List<UserQuestionAnswer>) {
        if (interaction.rpcId in _state.value.interactionResponsesInProgress) return
        respondToInteraction(interaction.rpcId) {
            api.answerQuestions(interaction.rpcId, interaction.sessionId, answers)
        }
    }

    fun cancelQuestions(interaction: PendingInteraction.Question) {
        if (interaction.rpcId in _state.value.interactionResponsesInProgress) return
        respondToInteraction(interaction.rpcId) { api.cancelQuestions(interaction.rpcId) }
    }

    fun saveDeepSeekApiKey(value: String) {
        val key = value.trim()
        if (key.isEmpty() || _state.value.credentialSaving) return
        viewModelScope.launch {
            _state.update { it.copy(credentialSaving = true, credentialMessage = null) }
            runCatching { api.setCredential(DEEPSEEK_API_KEY, key) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            deepSeekCredentialConfigured = true,
                            credentialSaving = false,
                            credentialMessage = "API Key 已写入 DSH 私有凭据文件",
                        )
                    }
                    reloadApiBalance()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(credentialSaving = false, credentialMessage = error.message)
                    }
                }
        }
    }

    override fun onOpen(stream: DshApiClient.Stream) {
        _state.update { it.copy(eventStreamsOpen = it.eventStreamsOpen + stream) }
    }

    override fun onEvent(stream: DshApiClient.Stream, event: DshServerEvent) {
        _state.update {
            it.copy(recentEvents = (listOf(event) + it.recentEvents).take(MAX_VISIBLE_EVENTS))
        }
        if (stream == DshApiClient.Stream.Mux) applyConversationEvent(event)
    }

    override fun onFailure(stream: DshApiClient.Stream, error: Throwable) {
        _state.update {
            it.copy(
                eventStreamsOpen = it.eventStreamsOpen - stream,
                hostError = "${stream.name} 事件流：${error.message}",
            )
        }
    }

    override fun onClosed(stream: DshApiClient.Stream) {
        _state.update { it.copy(eventStreamsOpen = it.eventStreamsOpen - stream) }
    }

    override fun onCleared() {
        closeStreams()
        api.close()
    }

    private fun openStreams() {
        DshApiClient.Stream.entries.forEach { stream ->
            sockets[stream] = api.open(stream, this)
        }
    }

    private fun closeStreams() {
        sockets.values.forEach(WebSocket::cancel)
        sockets.clear()
    }

    private fun applyConversationEvent(serverEvent: DshServerEvent) {
        QueueFeatureCodec.snapshot(serverEvent, _state.value.sessionId)?.let { items ->
            _state.update { current ->
                current.copy(queue = current.queue.copy(items = items, operationsInProgress = emptySet()))
            }
            return
        }
        JobsFeatureCodec.snapshot(serverEvent, _state.value.sessionId)?.let { items ->
            _state.update { current -> current.copy(jobs = JobsFeatureState(items)) }
            return
        }
        DshProtocol.pendingInteraction(serverEvent)?.let { pending ->
            _state.update { current ->
                val interactions = current.pendingInteractions
                    .filterNot { it.rpcId == pending.rpcId } + pending
                current.copy(pendingInteractions = interactions, conversationError = null)
            }
            return
        }
        DshProtocol.resolvedInteractionId(serverEvent)?.let { resolvedId ->
            _state.update { current ->
                val resolved = current.pendingInteractions.filter { interaction ->
                    interaction.rpcId == resolvedId ||
                        (interaction is PendingInteraction.Approval && interaction.approvalId == resolvedId)
                }
                current.copy(
                    pendingInteractions = current.pendingInteractions - resolved.toSet(),
                    interactionResponsesInProgress = current.interactionResponsesInProgress -
                        (resolved.map { it.rpcId }.toSet() + resolvedId),
                )
            }
            return
        }
        var reloadSessions = false
        _state.update { current ->
            val reduction = ConversationReducer.reduce(
                current = ConversationState(
                    messages = current.messages,
                    projections = current.projectionSnapshot,
                    promptSubmitting = current.promptSubmitting,
                    conversationError = current.conversationError,
                    planMode = current.planMode,
                ),
                serverEvent = serverEvent,
                selectedSessionId = current.sessionId,
            )
            reloadSessions = reduction.reloadSessionList
            current.copy(
                messages = reduction.state.messages,
                projectionSnapshot = reduction.state.projections,
                promptSubmitting = reduction.state.promptSubmitting,
                conversationError = reduction.state.conversationError,
                planMode = reduction.state.planMode,
            )
        }
        if (reloadSessions) viewModelScope.launch { reloadSessionList() }
        if (reloadSessions) reloadApiBalance()
    }

    private fun respondToInteraction(rpcId: String, response: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    interactionResponsesInProgress = it.interactionResponsesInProgress + rpcId,
                    conversationError = null,
                )
            }
            runCatching { response() }
                .onSuccess {
                    _state.update {
                        it.copy(
                            pendingInteractions = it.pendingInteractions.filterNot { pending ->
                                pending.rpcId == rpcId
                            },
                            interactionResponsesInProgress = it.interactionResponsesInProgress - rpcId,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            interactionResponsesInProgress = it.interactionResponsesInProgress - rpcId,
                            conversationError = error.message,
                        )
                    }
                }
        }
    }

    private fun runtimeAction(
        waitForHost: Boolean,
        action: suspend () -> com.smartaodi.dshandroid.runtime.RuntimeActionResult,
    ) {
        if (_state.value.runtimeActionInProgress) return
        viewModelScope.launch {
            _state.update { it.copy(runtimeActionInProgress = true, runtimeActionMessage = null) }
            val result = action()
            _state.update {
                it.copy(runtimeActionInProgress = false, runtimeActionMessage = result.message)
            }
            reload(
                clearActionMessage = false,
                hostProbeAttempts = if (result.success && waitForHost) HOST_START_PROBE_ATTEMPTS else 1,
            )
        }
    }

    private suspend fun reload(
        clearActionMessage: Boolean,
        hostProbeAttempts: Int = 1,
    ) {
        closeStreams()
        _state.update {
            it.copy(
                loading = true,
                runtime = RuntimeProbe.loading(),
                host = null,
                hostError = null,
                eventStreamsOpen = emptySet(),
                recentEvents = emptyList(),
                pendingInteractions = emptyList(),
                interactionResponsesInProgress = emptySet(),
                sessions = emptyList(),
                sessionLoading = false,
                modelDirectory = null,
                modelSelectionInProgress = false,
                modelMessage = null,
                planMode = false,
                planModeChanging = false,
                projectionSnapshot = null,
                apiBalance = null,
                history = HistoryFeatureState(),
                queue = QueueFeatureState(),
                workspaces = WorkspaceFeatureState(),
                commands = CommandsFeatureState(),
                jobs = JobsFeatureState(),
                runtimeActionMessage = if (clearActionMessage) null else it.runtimeActionMessage,
            )
        }

        val runtime = runtimeProbe.probe()
        suspend fun compatibleHostDescription(): HostDescription {
            val host = api.hostDescription()
            val dshVersion = runtime.dshVersion
                ?: throw DshProtocolException("Installed runtime does not declare its DSH version")
            DshSurfaceAdapter.requireCompatibleDshVersion(dshVersion)
            return host
        }

        var hostResult = runCatching { compatibleHostDescription() }
        var attempt = 1
        while (hostResult.isFailure && attempt < hostProbeAttempts) {
            delay(HOST_START_PROBE_DELAY_MS)
            hostResult = runCatching { compatibleHostDescription() }
            attempt += 1
        }
        _state.update {
            it.copy(
                loading = false,
                runtime = runtime,
                host = hostResult.getOrNull(),
                hostError = hostResult.exceptionOrNull()?.message,
            )
        }
        if (hostResult.isSuccess) {
            openStreams()
            runCatching { api.credentialConfigured(DEEPSEEK_API_KEY) }
                .onSuccess { configured ->
                    _state.update { it.copy(deepSeekCredentialConfigured = configured) }
                    if (configured) reloadApiBalance()
                }
            val sessions = runCatching { api.sessions() }.getOrDefault(emptyList())
            _state.update { it.copy(sessions = sessions) }
            reloadWorkspaces()
            val rootSessions = sessions.filter { it.origin != "subagent" }
            val sessionId = _state.value.sessionId ?: rootSessions.firstOrNull { !it.blank }?.sessionId
                ?: rootSessions.firstOrNull()?.sessionId
            if (_state.value.sessionId == null && sessionId != null) {
                _state.update { it.copy(sessionId = sessionId) }
            }
            if (sessionId != null) {
                _state.update { current ->
                    current.copy(planMode = sessions.firstOrNull { it.sessionId == sessionId }?.planMode == true)
                }
                runCatching { api.sessionModels(sessionId) }
                    .onSuccess { directory -> _state.update { it.copy(modelDirectory = directory) } }
                    .onFailure { error -> _state.update { it.copy(modelMessage = error.message) } }
                reloadCommands(sessionId)
                runCatching { api.sessionHistory(sessionId) }
                    .onSuccess { history ->
                        val replacement = HistoryFeature.replace(history)
                        _state.update {
                            it.copy(
                                messages = replacement.messages,
                                history = replacement.state,
                                projectionSnapshot = history.projections,
                                promptSubmitting = sessions.firstOrNull { session ->
                                    session.sessionId == sessionId
                                }?.running == true,
                            )
                        }
                    }
                    .onFailure { error -> _state.update { it.copy(conversationError = error.message) } }
            }
        }
    }

    private suspend fun reloadSessionList() {
        runCatching { api.sessions() }
            .onSuccess { sessions -> _state.update { it.copy(sessions = sessions) } }
    }

    private suspend fun reloadWorkspaces() {
        runCatching { api.workspaces() }
            .onSuccess { directory ->
                _state.update {
                    it.copy(
                        workspaces = it.workspaces.copy(
                            items = directory.items,
                            archivedSessionIds = directory.archivedSessionIds,
                            loading = false,
                            error = null,
                        ),
                    )
                }
            }
            .onFailure { error ->
                _state.update { it.copy(workspaces = it.workspaces.copy(loading = false, error = error.message)) }
            }
    }

    private suspend fun reloadWorkspaceSurface() {
        reloadSessionList()
        reloadWorkspaces()
    }

    private fun workspaceOperation(operation: suspend () -> Unit) {
        if (_state.value.workspaces.operationInProgress) return
        viewModelScope.launch {
            _state.update { it.copy(workspaces = it.workspaces.copy(operationInProgress = true, error = null)) }
            runCatching { operation() }
                .onSuccess {
                    reloadWorkspaceSurface()
                    _state.update { it.copy(workspaces = it.workspaces.copy(operationInProgress = false)) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(workspaces = it.workspaces.copy(operationInProgress = false, error = error.message))
                    }
                }
        }
    }

    private fun queueOperation(itemId: String, operation: suspend (String) -> Unit) {
        val sessionId = _state.value.sessionId ?: return
        if (itemId in _state.value.queue.operationsInProgress) return
        viewModelScope.launch {
            _state.update {
                it.copy(queue = it.queue.copy(operationsInProgress = it.queue.operationsInProgress + itemId))
            }
            runCatching { operation(sessionId) }
                .onSuccess {
                    _state.update { current ->
                        current.copy(
                            queue = current.queue.copy(
                                operationsInProgress = current.queue.operationsInProgress - itemId,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            queue = it.queue.copy(operationsInProgress = it.queue.operationsInProgress - itemId),
                            conversationError = error.message,
                        )
                    }
                }
        }
    }

    private fun reloadModels(sessionId: String) {
        viewModelScope.launch {
            runCatching { api.sessionModels(sessionId) }
                .onSuccess { directory -> _state.update { it.copy(modelDirectory = directory) } }
                .onFailure { error -> _state.update { it.copy(modelMessage = error.message) } }
        }
    }

    private fun reloadCommands(sessionId: String) {
        viewModelScope.launch {
            _state.update { it.copy(commands = it.commands.copy(loading = true, error = null)) }
            runCatching { api.commands(sessionId) }
                .onSuccess { commands ->
                    _state.update { current ->
                        if (current.sessionId != sessionId) current
                        else current.copy(commands = CommandsFeatureState(items = commands))
                    }
                }
                .onFailure { error ->
                    _state.update { current ->
                        if (current.sessionId != sessionId) current
                        else current.copy(commands = CommandsFeatureState(error = error.message))
                    }
                }
        }
    }

    private fun reloadApiBalance() {
        viewModelScope.launch {
            runCatching { api.deepSeekBalance() }
                .onSuccess { balance -> _state.update { it.copy(apiBalance = balance) } }
        }
    }

    companion object {
        private const val MAX_VISIBLE_EVENTS = 8
        private const val DEEPSEEK_API_KEY = "DEEPSEEK_API_KEY"
        private const val HOST_START_PROBE_ATTEMPTS = 12
        private const val HOST_START_PROBE_DELAY_MS = 250L
    }
}
