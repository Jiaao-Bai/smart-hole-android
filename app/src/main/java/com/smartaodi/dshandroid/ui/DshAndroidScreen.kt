package com.smartaodi.dshandroid.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartaodi.dshandroid.HarnessUiState
import com.smartaodi.dshandroid.HarnessViewModel
import com.smartaodi.dshandroid.R
import com.smartaodi.dshandroid.features.CoreSessionFeatures
import com.smartaodi.dshandroid.features.SessionFeature
import com.smartaodi.dshandroid.features.Subagent
import com.smartaodi.dshandroid.protocol.ChatMessage
import com.smartaodi.dshandroid.protocol.ChatRole
import com.smartaodi.dshandroid.protocol.DshApiClient
import com.smartaodi.dshandroid.protocol.ModelSelection
import com.smartaodi.dshandroid.protocol.PendingInteraction
import com.smartaodi.dshandroid.protocol.SessionSummary
import com.smartaodi.dshandroid.protocol.ToolActivity
import com.smartaodi.dshandroid.protocol.ToolCallState
import com.smartaodi.dshandroid.protocol.ToolCallTrace
import com.smartaodi.dshandroid.protocol.ToolPresentation
import com.smartaodi.dshandroid.protocol.UserQuestionAnswer
import com.smartaodi.dshandroid.runtime.RuntimeProbe
import com.smartaodi.dshandroid.ui.features.history.HistoryPager
import com.smartaodi.dshandroid.ui.features.queue.QueueComposer
import com.smartaodi.dshandroid.ui.features.workspace.WorkspaceSessionsPanel
import com.smartaodi.dshandroid.ui.features.jobs.JobsDock
import com.smartaodi.dshandroid.ui.features.reasoning.ReasoningBlock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ReadyGreen = Color(0xFF18A66A)
private val WarningAmber = Color(0xFFEAA43A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DshAndroidScreen(viewModel: HarnessViewModel) {
    val state by viewModel.state.collectAsState()
    var showSystemPanel by remember { mutableStateOf(false) }
    var showSessions by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var positionedSessionId by remember { mutableStateOf<String?>(null) }
    var followConversationTail by remember { mutableStateOf(true) }
    val visibleMessages = state.messages
    val activeInteractions = remember(state.pendingInteractions, state.sessionId) {
        state.pendingInteractions.filter { it.sessionId == state.sessionId }
    }
    val coreFeatures = remember(state.sessionId, state.projectionSnapshot, state.sessions) {
        state.coreFeatures
    }

    LaunchedEffect(listState, state.sessionId) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && positionedSessionId == state.sessionId) {
                val layout = listState.layoutInfo
                val lastVisibleItem = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                followConversationTail = lastVisibleItem >= layout.totalItemsCount - 1
            }
        }
    }

    LaunchedEffect(
        state.sessionId,
        state.messages.lastOrNull(),
        state.promptSubmitting,
        activeInteractions.size,
    ) {
        withFrameNanos { }
        val lastItem = listState.layoutInfo.totalItemsCount - 1
        if (lastItem < 0) return@LaunchedEffect

        val sessionId = state.sessionId
        if (sessionId != null && positionedSessionId != sessionId && state.messages.isNotEmpty()) {
            listState.scrollToItem(lastItem)
            positionedSessionId = sessionId
            followConversationTail = true
            return@LaunchedEffect
        }

        if (followConversationTail && positionedSessionId == sessionId) {
            if (state.promptSubmitting) listState.scrollToItem(lastItem)
            else listState.animateScrollToItem(lastItem)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            HarnessTopBar(
                state = state,
                onNewSession = viewModel::newSession,
                onOpenSessions = { showSessions = true },
                onOpenSystem = { showSystemPanel = true },
            )
        },
        bottomBar = {
            QueueComposer(
                draft = draft,
                onDraftChange = { draft = it },
                ready = state.host != null && state.deepSeekCredentialConfigured && !state.sessionLoading,
                submitting = state.promptSubmitting,
                planMode = state.planMode,
                planModeChanging = state.planModeChanging,
                contextPressure = coreFeatures.metrics?.context,
                tokenUsage = coreFeatures.metrics?.tokenUsage,
                apiBalance = state.apiBalance.takeIf {
                    state.modelDirectory?.current?.provider == "deepseek-official"
                },
                queue = state.queue,
                commands = state.commands,
                onPlanModeChange = viewModel::setPlanMode,
                onSend = { mode ->
                    val message = draft.trim()
                    if (message.isNotEmpty()) {
                        viewModel.sendPrompt(message, mode)
                        draft = ""
                    }
                },
                onCancel = viewModel::cancelTurn,
                onEditQueueItem = viewModel::editQueueItem,
                onRemoveQueueItem = viewModel::removeQueueItem,
                onSteerQueueItem = viewModel::steerQueueItem,
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (state.loading) {
                item(key = "loading") { LoadingConversation() }
            } else {
                if (state.host == null || !state.deepSeekCredentialConfigured) {
                    item(key = "attention") {
                        AttentionBanner(
                            state = state,
                            onOpenSystem = { showSystemPanel = true },
                        )
                    }
                }

                if (coreFeatures.hasVisibleContent) {
                    item(key = "session-projections-${state.projectionSnapshot?.asOfSeq}") {
                        SessionProjectionDock(coreFeatures, viewModel::openSession)
                    }
                }

                if (state.jobs.items.isNotEmpty()) {
                    item(key = "session-jobs") { JobsDock(state.jobs) }
                }

                if (state.messages.isNotEmpty()) {
                    item(key = "history-pager") {
                        HistoryPager(state.history, viewModel::loadOlderHistory)
                    }
                }

                if (state.messages.isEmpty()) {
                    item(key = "empty") {
                        EmptyConversation(
                            state = state,
                            enabled = state.host != null && state.deepSeekCredentialConfigured,
                            onSuggestion = { draft = it },
                        )
                    }
                } else {
                    itemsIndexed(visibleMessages, key = { _, message -> message.id }) { index, message ->
                        ChatMessageRow(
                            message = message,
                            showAgentIdentity = message.role != ChatRole.User &&
                                (index == 0 || visibleMessages[index - 1].role == ChatRole.User),
                        )
                    }
                }

                if (state.promptSubmitting && state.messages.none { it.streaming || it.toolActivity?.running == true }) {
                    if (activeInteractions.isEmpty()) {
                        item(key = "thinking") { ThinkingRow() }
                    }
                }

                items(activeInteractions, key = PendingInteraction::rpcId) { interaction ->
                    PendingInteractionCard(
                        interaction = interaction,
                        busy = interaction.rpcId in state.interactionResponsesInProgress,
                        onApproval = viewModel::answerApproval,
                        onQuestions = viewModel::answerQuestions,
                        onCancelQuestions = viewModel::cancelQuestions,
                    )
                }

                state.conversationError?.let { error ->
                    item(key = "error-$error") { ConversationError(error) }
                }
            }

            item(key = "conversation-bottom") { Spacer(Modifier.height(4.dp)) }
        }
    }

    if (showSystemPanel) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSystemPanel = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            SystemPanel(
                state = state,
                viewModel = viewModel,
                onClose = { showSystemPanel = false },
            )
        }
    }

    if (showSessions) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSessions = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            WorkspaceSessionsPanel(
                state = state,
                onNewSession = {
                    showSessions = false
                    viewModel.newSession()
                },
                onOpenSession = { sessionId ->
                    showSessions = false
                    viewModel.openSession(sessionId)
                },
                onNewSessionInWorkspace = { workspaceId ->
                    showSessions = false
                    viewModel.newSessionInWorkspace(workspaceId)
                },
                onRenameWorkspace = viewModel::renameWorkspace,
                onDeleteWorkspace = viewModel::deleteWorkspace,
                onRenameSession = viewModel::renameSession,
                onForkSession = { sessionId ->
                    showSessions = false
                    viewModel.forkSession(sessionId)
                },
                onArchiveSession = viewModel::archiveSession,
                onClose = { showSessions = false },
            )
        }
    }
}

@Composable
private fun SessionProjectionDock(
    features: CoreSessionFeatures,
    onOpenSubagent: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            val work = features.work
            work?.goal?.let { goal ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("目标", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        goalPhaseName(goal.phase),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(goal.objective, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "进度轮次 ${goal.roundsStarted}/${goal.maxGoalRounds}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                goal.blockedReason?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (work?.goal != null && work.todos.isNotEmpty()) ProjectionDivider()
            if (work?.todos?.isNotEmpty() == true) {
                Text("任务", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                work.todos.forEach { todo ->
                    Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                        Text(
                            when (todo.status) {
                                "completed" -> "✓"
                                "in_progress" -> "●"
                                else -> "○"
                            },
                            modifier = Modifier.width(22.dp),
                            color = when (todo.status) {
                                "completed" -> ReadyGreen
                                "in_progress" -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            todo.content,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (todo.status == "completed") {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
            val metrics = features.metrics
            if (metrics != null) {
                if (work?.let { it.goal != null || it.todos.isNotEmpty() } == true) ProjectionDivider()
                SessionMetricsRow(metrics)
            }
            val subagents = features.subagents?.roots.orEmpty()
            if (subagents.isNotEmpty()) {
                if (work?.let { it.goal != null || it.todos.isNotEmpty() } == true || metrics != null) {
                    ProjectionDivider()
                }
                Text("子 Agent", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                subagents.forEach { subagent ->
                    SubagentRow(subagent, depth = 0, onOpenSubagent)
                }
            }
        }
    }
}

@Composable
private fun ProjectionDivider() {
    Spacer(Modifier.height(10.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SessionMetricsRow(metrics: SessionFeature.Metrics) {
    val parts = buildList {
        metrics.context?.let { context ->
            val used = context.projectedTokens ?: context.pressureTokens
            if (used != null && context.contextWindow != null) {
                add("上下文 ${compactCount(used)}/${compactCount(context.contextWindow)}")
            } else if (used != null) {
                add("上下文 ${compactCount(used)}")
            }
        }
        metrics.stats?.turns?.takeIf { it > 0 }?.let { add("$it 轮") }
        metrics.tokenUsage?.total?.takeIf { it > 0 }?.let { add("${compactCount(it)} tokens") }
    }
    if (parts.isEmpty()) return
    Text(
        parts.joinToString("  ·  "),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SubagentRow(subagent: Subagent, depth: Int, onOpen: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(subagent.sessionId) }
            .padding(start = (depth * 14).dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (subagent.running) "●" else "✓",
            modifier = Modifier.width(22.dp),
            color = if (subagent.running) MaterialTheme.colorScheme.primary else ReadyGreen,
            fontWeight = FontWeight.Bold,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                subagent.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
            val details = buildList {
                subagent.mode?.let { add(if (it == "continuable") "可继续" else "单次") }
                subagent.durationMs?.let { add(compactDuration(it)) }
                subagent.tokenUsage?.total?.takeIf { it > 0 }?.let { add("${compactCount(it)} tokens") }
            }
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    subagent.children.forEach { child -> SubagentRow(child, depth + 1, onOpen) }
}

private fun compactCount(value: Long): String = when {
    value >= 1_000_000 -> "%.1fM".format(Locale.US, value / 1_000_000.0)
    value >= 1_000 -> "%.1fK".format(Locale.US, value / 1_000.0)
    else -> value.toString()
}.removeSuffix(".0K").removeSuffix(".0M")

private fun compactDuration(milliseconds: Long): String = when {
    milliseconds >= 60_000 -> "${milliseconds / 60_000}m"
    else -> "${milliseconds / 1_000}s"
}

private fun goalPhaseName(phase: String): String = when (phase) {
    "active" -> "进行中"
    "paused" -> "已暂停"
    "blocked" -> "受阻"
    "complete" -> "已完成"
    else -> phase
}

@Composable
private fun LoadingConversation() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 110.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark(72.dp)
        Spacer(Modifier.height(20.dp))
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            "正在连接本机 Smart Hole…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(5.dp))
        PoweredByLabel()
    }
}

@Composable
private fun HarnessTopBar(
    state: HarnessUiState,
    onNewSession: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenSystem: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(
                        enabled = !state.loading && state.host != null,
                        onClickLabel = "打开对话列表",
                        onClick = onOpenSessions,
                    )
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandMark(38.dp)
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = activeSessionTitle(state),
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("▾", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val ready = state.host != null && state.deepSeekCredentialConfigured
                        Box(
                            Modifier
                                .size(6.dp)
                                .background(if (ready) ReadyGreen else WarningAmber, CircleShape),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = when {
                                state.loading -> "正在检查本机运行环境"
                                state.sessionLoading -> "正在打开对话…"
                                state.host == null -> "Host 未运行"
                                !state.deepSeekCredentialConfigured -> "需要模型凭据"
                                else -> "Powered by DeepSeek Harness"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            CircleTextButton(
                text = "＋",
                contentDescription = "新对话",
                enabled = state.host != null && !state.sessionLoading,
                onClick = onNewSession,
            )
            Spacer(Modifier.width(8.dp))
            CircleTextButton(
                text = "•••",
                contentDescription = "系统面板",
                onClick = onOpenSystem,
            )
        }
    }
}

@Composable
private fun CircleTextButton(
    text: String,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClickLabel = contentDescription, onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = if (text == "＋") 24.sp else 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = if (text == "＋") 0.sp else (-1).sp,
            )
        }
    }
}

@Composable
private fun AttentionBanner(state: HarnessUiState, onOpenSystem: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpenSystem),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("!", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.host == null) "Smart Hole 暂未就绪" else "还差最后一步",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (state.host == null) "打开系统面板检查并启动本机 Host" else "配置 DeepSeek API Key 后即可开始",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("打开", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyConversation(
    state: HarnessUiState,
    enabled: Boolean,
    onSuggestion: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF0D120F), Color(0xFF14382D), Color(0xFF0D120F)),
                    ),
                )
                .padding(horizontal = 24.dp, vertical = 26.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BrandMark(104.dp)
                Spacer(Modifier.height(17.dp))
                Text(
                    text = "SMART HOLE",
                    color = Color(0xFFFFF3D2),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.4.sp,
                )
                Spacer(Modifier.height(3.dp))
                PoweredByLabel(onDark = true)
                Spacer(Modifier.height(13.dp))
                Text(
                    text = "打开 Android Root 世界的原生 Agent",
                    color = Color(0xFFE5F4EC),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(15.dp))
                HeroStatusPill(
                    ready = enabled,
                    text = if (enabled) "${modelDisplayName(state)} · 本机 Root 已就绪" else "等待本机 Host 就绪",
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = "今天想让 Smart Hole 做什么？",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = "直接让 DeepSeek Harness 读代码、操作文件，或调用 Android 系统能力。",
            modifier = Modifier.widthIn(max = 330.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        Suggestion("检查一下手机当前状态", enabled, onSuggestion)
        Spacer(Modifier.height(10.dp))
        Suggestion("看看工作目录里有哪些项目", enabled, onSuggestion)
        Spacer(Modifier.height(10.dp))
        Suggestion("帮我分析最近的系统日志", enabled, onSuggestion)
    }
}

@Composable
private fun HeroStatusPill(ready: Boolean, text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (ready) Color(0xFF55E0B5).copy(alpha = 0.14f) else Color.White.copy(alpha = 0.09f),
        border = BorderStroke(
            1.dp,
            if (ready) Color(0xFF55E0B5).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.18f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).background(if (ready) Color(0xFF55E0B5) else WarningAmber, CircleShape))
            Spacer(Modifier.width(7.dp))
            Text(text, color = Color(0xFFE8F7F0), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun PoweredByLabel(onDark: Boolean = false) {
    Text(
        text = "Powered by DeepSeek Harness",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.35.sp,
        color = if (onDark) Color(0xFF55E0B5) else MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Suggestion(text: String, enabled: Boolean, onSuggestion: (String) -> Unit) {
    Surface(
        modifier = Modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onSuggestion(text) },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text("→", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ChatMessageRow(message: ChatMessage, showAgentIdentity: Boolean) {
    when (message.role) {
        ChatRole.User -> UserMessage(message)
        ChatRole.Assistant -> AssistantMessage(message, showAgentIdentity)
        ChatRole.Tool -> ToolMessage(message)
    }
}

@Composable
private fun UserMessage(message: ChatMessage) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 380.dp),
            shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            SelectionContainer {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun AssistantMessage(message: ChatMessage, showIdentity: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showIdentity || message.streaming) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showIdentity) {
                    BrandMark(28.dp)
                    Spacer(Modifier.width(11.dp))
                    Text("Smart Hole", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "Powered by DeepSeek Harness",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (message.streaming) {
                    if (showIdentity) Spacer(Modifier.width(8.dp))
                    Text(
                        "正在生成",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        message.reasoning?.let {
            ReasoningBlock(it, message.streaming)
            Spacer(Modifier.height(9.dp))
        }
        if (message.text.isNotBlank()) {
            SelectionContainer {
                RichMessageText(message.text)
            }
        }
    }
}

@Composable
private fun ToolMessage(message: ChatMessage) {
    val activity = message.toolActivity ?: return
    var expanded by remember(message.id) { mutableStateOf(false) }
    val errors = activity.calls.count { it.state == ToolCallState.Error }
    val completed = activity.calls.count { it.state != ToolCallState.Running }
    val title = when {
        activity.running -> "正在执行 · $completed/${activity.calls.size} 个操作"
        errors > 0 -> "执行完成 · ${activity.calls.size} 个操作，$errors 个失败"
        else -> "已完成 · ${activity.calls.size} 个操作"
    }
    val summary = toolBreakdown(activity)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .clickable(enabled = activity.calls.isNotEmpty()) { expanded = !expanded },
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToolGlyph()
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    ToolActivityTitle(title, activity.running)
                    Text(
                        summary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (expanded) "⌃" else "⌄",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                Spacer(Modifier.height(6.dp))
                activity.calls.forEachIndexed { index, call ->
                    ToolCallRow(call)
                    if (index != activity.calls.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 26.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolActivityTitle(text: String, running: Boolean) {
    if (!running) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        return
    }
    val transition = rememberInfiniteTransition(label = "tool-activity")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "tool-activity-alpha",
    )
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
    )
}

private fun toolBreakdown(activity: ToolActivity): String {
    if (activity.calls.isEmpty()) return "准备调用工具"
    return activity.calls
        .groupingBy(::toolCategoryName)
        .eachCount()
        .entries
        .joinToString(" · ") { (name, count) -> if (count == 1) name else "$name ×$count" }
}

private fun toolCategoryName(call: ToolCallTrace): String = when (
    call.resultPresentation?.card ?: call.callPresentation?.card
) {
    "terminal" -> "终端"
    "diff" -> "修改"
    "search" -> "搜索"
    "read" -> "读取"
    "web" -> "网页"
    "generic" -> call.callPresentation?.kind?.let(::toolKindName) ?: toolDisplayName(call.name)
    null -> toolDisplayName(call.name)
    else -> "插件"
}

private fun toolKindName(kind: String): String = when (kind) {
    "read" -> "读取"
    "edit" -> "编辑"
    "delete" -> "删除"
    "move" -> "移动"
    "search" -> "搜索"
    "execute" -> "执行"
    "fetch" -> "获取"
    else -> "工具"
}

private fun toolDisplayName(name: String): String = when (name) {
    "bash" -> "Bash"
    "pwsh" -> "PowerShell"
    "android_system" -> "Android"
    "read" -> "读取"
    "write" -> "写入"
    "edit" -> "编辑"
    "web_search" -> "搜索"
    "web_fetch" -> "网页"
    "run_code" -> "代码"
    "tool" -> "工具"
    else -> name
}

@Composable
private fun ToolCallRow(call: ToolCallTrace) {
    val presentation = presentationBody(call)
    val expandable = presentation != null || call.input.isNotBlank() || !call.output.isNullOrBlank()
    var expanded by remember(call.callId) { mutableStateOf(false) }
    val presentationTitle = call.resultPresentation?.title ?: call.callPresentation?.title
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = expandable) { expanded = !expanded }
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolStateGlyph(call.state)
            Spacer(Modifier.width(8.dp))
            Text(
                presentationTitle ?: toolDisplayName(call.name),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                call.summary.takeUnless { it == presentationTitle } ?: toolCategoryName(call),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = if (call.state == ToolCallState.Error) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (expandable) Text(if (expanded) "⌃" else "⌄", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) {
            Spacer(Modifier.height(7.dp))
            if (presentation != null) {
                ToolIoBlock(
                    presentation.first,
                    presentation.second,
                    error = call.state == ToolCallState.Error,
                )
            } else {
                ToolIoBlock("IN", call.input)
                call.output?.takeIf { it.isNotBlank() }?.let { output ->
                    Spacer(Modifier.height(6.dp))
                    ToolIoBlock("OUT", output, error = call.state == ToolCallState.Error)
                }
            }
        }
    }
}

private fun presentationBody(call: ToolCallTrace): Pair<String, String>? {
    val pending = call.callPresentation
    val result = call.resultPresentation
    val card = result?.card ?: pending?.card ?: return null
    val body = when (card) {
        "terminal" -> terminalBody(pending, result)
        "diff" -> diffBody(result?.takeIf { it.diffs.isNotEmpty() } ?: pending)
        "search" -> searchBody(result?.search)
        "read" -> readBody(result?.read)
        "web" -> webBody(result?.web)
        "generic" -> result?.content
            ?: pending?.content
            ?: pending?.rawInput
            ?: call.output
            ?: call.input
        else -> result?.rawJson ?: pending?.rawJson
    }.takeIf { !it.isNullOrBlank() } ?: return null
    val label = when (card) {
        "terminal" -> "TERM"
        "diff" -> "DIFF"
        "search" -> "FIND"
        "read" -> "READ"
        "web" -> "WEB"
        "generic" -> "VIEW"
        else -> card.uppercase(Locale.ROOT).take(8)
    }
    return label to body
}

private fun terminalBody(pending: ToolPresentation?, result: ToolPresentation?): String = buildList {
    pending?.cwd?.let { add("cwd: $it") }
    pending?.title?.let(::add)
    result?.output?.takeIf { it.isNotBlank() }?.let(::add)
    result?.exitCode?.let { add("exit: $it") }
    result?.signal?.let { add("signal: $it") }
}.joinToString("\n")

private fun diffBody(presentation: ToolPresentation?): String? = presentation?.diffs
    ?.takeIf { it.isNotEmpty() }
    ?.joinToString("\n\n") { diff ->
        buildString {
            append(diff.path)
            diff.oldText?.let { append("\n--- before\n").append(it) }
            append("\n+++ after\n").append(diff.newText)
        }
    }

private fun searchBody(search: com.smartaodi.dshandroid.protocol.ToolSearchPresentation?): String? {
    search ?: return null
    val content = if (search.shape == "paths") {
        search.paths.joinToString("\n")
    } else {
        search.files.joinToString("\n\n") { file ->
            buildString {
                append(file.path)
                file.matches.forEach { append("\n").append(it.lineNumber).append(": ").append(it.line) }
            }
        }
    }
    val cap = if (search.truncated) "\n… ${search.total ?: "更多"} 条结果，已截断" else ""
    return (content + cap).takeIf { it.isNotBlank() }
}

private fun readBody(read: com.smartaodi.dshandroid.protocol.ToolReadPresentation?): String? {
    read ?: return null
    return buildString {
        append(read.path).append(" · ").append(read.lines.size).append("/").append(read.totalLines).append(" 行")
        read.lines.forEach { append("\n").append(it.number.toString().padStart(4)).append("  ").append(it.text) }
    }
}

private fun webBody(web: com.smartaodi.dshandroid.protocol.ToolWebPresentation?): String? {
    web ?: return null
    return when (web.kind) {
        "fetch" -> buildString {
            web.statusCode?.let { append("HTTP ").append(it).append("\n") }
            append(web.url.orEmpty())
            if (web.truncated) append("\n内容已截断")
        }.takeIf { it.isNotBlank() }
        else -> buildString {
            web.answer?.takeIf { it.isNotBlank() }?.let { append(it).append("\n\n") }
            web.sources.forEachIndexed { index, source ->
                if (index > 0) append("\n\n")
                append(source.title ?: source.url).append("\n").append(source.url)
                source.snippet?.let { append("\n").append(it) }
            }
            if (web.truncated) append("\n\n来源列表已截断")
        }.takeIf { it.isNotBlank() }
    }
}

@Composable
private fun ToolStateGlyph(state: ToolCallState) {
    when (state) {
        ToolCallState.Running -> CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.8.dp)
        ToolCallState.Success -> Text("✓", color = ReadyGreen, fontWeight = FontWeight.Bold)
        ToolCallState.Error -> Text("×", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        ToolCallState.Stopped -> Text("■", color = WarningAmber, fontSize = 9.sp)
    }
}

@Composable
private fun ToolIoBlock(label: String, text: String, error: Boolean = false) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
            Text(
                label,
                modifier = Modifier.width(30.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectionContainer {
                Text(
                    text,
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun RichMessageText(text: String) {
    val blocks = remember(text) { parseMessageBlocks(text) }
    MarkdownBlocks(blocks)
}

@Composable
private fun MarkdownBlocks(blocks: List<MessageBlock>, compact: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 10.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MessageBlock.Prose -> MarkdownProse(block)
                is MessageBlock.CodeBlock -> CodeBlock(block)
                is MessageBlock.ListBlock -> MarkdownList(block)
                is MessageBlock.Quote -> MarkdownQuote(block)
                is MessageBlock.Table -> MarkdownTable(block)
                MessageBlock.Divider -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun MarkdownProse(block: MessageBlock.Prose) {
    val style = when (block.headingLevel) {
        1 -> MaterialTheme.typography.headlineMedium
        2 -> MaterialTheme.typography.headlineSmall
        3 -> MaterialTheme.typography.titleLarge
        4 -> MaterialTheme.typography.titleMedium
        5, 6 -> MaterialTheme.typography.titleSmall
        else -> MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp)
    }
    InlineText(
        spans = block.inlines,
        style = style.copy(fontWeight = if (block.headingLevel != null) FontWeight.Bold else style.fontWeight),
    )
}

@Composable
private fun InlineText(
    spans: List<InlineSpan>,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    val annotated = remember(spans, linkColor, codeBackground) {
        buildAnnotatedString {
            spans.forEach { span ->
                val decorations = buildList {
                    if (span.strikethrough) add(TextDecoration.LineThrough)
                    if (span.url != null) add(TextDecoration.Underline)
                }
                val spanStyle = SpanStyle(
                    color = if (span.url != null) linkColor else Color.Unspecified,
                    background = if (span.code) codeBackground else Color.Unspecified,
                    fontFamily = if (span.code) FontFamily.Monospace else null,
                    fontWeight = if (span.bold) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    textDecoration = decorations.takeIf { it.isNotEmpty() }?.let(TextDecoration::combine),
                )
                if (span.url != null) {
                    withLink(LinkAnnotation.Url(span.url)) {
                        withStyle(spanStyle) { append(span.text) }
                    }
                } else {
                    withStyle(spanStyle) { append(span.text) }
                }
            }
        }
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = style.copy(color = MaterialTheme.colorScheme.onBackground),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun MarkdownList(block: MessageBlock.ListBlock) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        block.items.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    if (block.ordered) "${block.start + index}." else "•",
                    modifier = Modifier.width(26.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Box(modifier = Modifier.weight(1f)) { MarkdownBlocks(item, compact = true) }
            }
        }
    }
}

@Composable
private fun MarkdownQuote(block: MessageBlock.Quote) {
    Row(verticalAlignment = Alignment.Top) {
        Text("▍", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) { MarkdownBlocks(block.blocks, compact = true) }
    }
}

@Composable
private fun MarkdownTable(block: MessageBlock.Table) {
    val rows = buildList {
        if (block.header.isNotEmpty()) add(block.header to true)
        block.rows.forEach { add(it to false) }
    }
    val columnCount = rows.maxOfOrNull { it.first.size } ?: 0
    Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        rows.forEach { (cells, header) ->
            Row {
                cells.forEachIndexed { index, cell ->
                    val width = when {
                        columnCount == 1 -> 300.dp
                        columnCount == 2 && index == 0 -> 108.dp
                        columnCount == 2 -> 192.dp
                        else -> 150.dp
                    }
                    Surface(
                        modifier = Modifier.width(width),
                        color = if (header) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        InlineText(
                            spans = cell,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(block: MessageBlock.CodeBlock) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Column {
            if (block.language.isNotEmpty()) {
                Text(
                    text = block.language,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            }
            SelectionContainer {
                Text(
                    text = block.text,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(14.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ThinkingRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BrandMark(28.dp)
        Spacer(Modifier.width(11.dp))
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            "Smart Hole 正在思考…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConversationError(error: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("本轮没有完成", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PendingInteractionCard(
    interaction: PendingInteraction,
    busy: Boolean,
    onApproval: (PendingInteraction.Approval, Boolean) -> Unit,
    onQuestions: (PendingInteraction.Question, List<UserQuestionAnswer>) -> Unit,
    onCancelQuestions: (PendingInteraction.Question) -> Unit,
) {
    when (interaction) {
        is PendingInteraction.Approval -> ApprovalInteractionCard(
            interaction = interaction,
            busy = busy,
            onDecision = { allow -> onApproval(interaction, allow) },
        )
        is PendingInteraction.Question -> QuestionInteractionCard(
            interaction = interaction,
            busy = busy,
            onAnswer = { answers -> onQuestions(interaction, answers) },
            onCancel = { onCancelQuestions(interaction) },
        )
    }
}

@Composable
private fun ApprovalInteractionCard(
    interaction: PendingInteraction.Approval,
    busy: Boolean,
    onDecision: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(17.dp)) {
            Text("等待授权", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.height(6.dp))
            Text(
                interaction.reason ?: "${interaction.toolName} 请求执行一次需要确认的操作",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                interaction.toolName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.72f),
            )
            Spacer(Modifier.height(15.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { onDecision(false) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text("拒绝") }
                Button(
                    onClick = { onDecision(true) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text(if (busy) "正在提交…" else "允许一次") }
            }
        }
    }
}

@Composable
private fun QuestionInteractionCard(
    interaction: PendingInteraction.Question,
    busy: Boolean,
    onAnswer: (List<UserQuestionAnswer>) -> Unit,
    onCancel: () -> Unit,
) {
    var selections by remember(interaction.rpcId) {
        mutableStateOf<Map<String, Set<String>>>(emptyMap())
    }
    var customAnswers by remember(interaction.rpcId) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)),
    ) {
        Column(modifier = Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Smart Hole 需要你的选择", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            interaction.questions.forEachIndexed { questionIndex, question ->
                if (questionIndex > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    question.header?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(question.question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    question.detail?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    question.options.forEach { option ->
                        val selected = option.label in selections[question.id].orEmpty()
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(13.dp))
                                .clickable(enabled = !busy) {
                                    val current = selections[question.id].orEmpty()
                                    val next = if (question.multiSelect) {
                                        if (selected) current - option.label else current + option.label
                                    } else {
                                        setOf(option.label)
                                    }
                                    selections = selections + (question.id to next)
                                    if (!question.multiSelect) customAnswers = customAnswers - question.id
                                },
                            shape = RoundedCornerShape(13.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                else MaterialTheme.colorScheme.outlineVariant,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(if (selected) "✓" else "○", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(9.dp))
                                Column {
                                    Text(option.label, fontWeight = FontWeight.Medium)
                                    option.description?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = customAnswers[question.id].orEmpty(),
                        onValueChange = { value ->
                            customAnswers = customAnswers + (question.id to value)
                            if (!question.multiSelect && value.isNotBlank()) selections = selections - question.id
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("其他回答（可选）") },
                        minLines = 1,
                        maxLines = 4,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        onAnswer(
                            interaction.questions.map { question ->
                                UserQuestionAnswer(
                                    id = question.id,
                                    selected = selections[question.id].orEmpty().toList(),
                                    custom = customAnswers[question.id]?.trim()?.takeIf { it.isNotEmpty() },
                                )
                            },
                        )
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text(if (busy) "正在提交…" else "提交回答") }
            }
        }
    }
}

@Composable
private fun BrandMark(size: androidx.compose.ui.unit.Dp) {
    Image(
        painter = painterResource(R.drawable.smart_hole_avatar_badge),
        contentDescription = "Smart Hole",
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.25f)),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun ToolGlyph() {
    Canvas(Modifier.size(18.dp)) {
        val color = Color(0xFF4F6FC7)
        drawLine(color, androidx.compose.ui.geometry.Offset(2f, size.height / 2), androidx.compose.ui.geometry.Offset(size.width - 2f, size.height / 2), 2.dp.toPx(), StrokeCap.Round)
        drawLine(color, androidx.compose.ui.geometry.Offset(size.width / 2, 2f), androidx.compose.ui.geometry.Offset(size.width / 2, size.height - 2f), 2.dp.toPx(), StrokeCap.Round)
        drawCircle(color, radius = 2.6.dp.toPx(), center = center)
    }
}

@Composable
private fun SystemPanel(
    state: HarnessUiState,
    viewModel: HarnessViewModel,
    onClose: () -> Unit,
) {
    var showDiagnostics by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 38.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Smart Hole 系统", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Powered by DeepSeek Harness · 手机 Agent · 本机 Root 运行时",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onClose) { Text("完成") }
            }
        }

        item {
            PanelSection("DSH Host") {
                val hostReady = state.host != null
                StatusRow(
                    label = when {
                        hostReady -> "正在本机运行"
                        state.runtime.hostRunning -> "Host 进程存在但无法连接"
                        else -> "当前未运行"
                    },
                    good = hostReady,
                    detail = state.host?.let {
                        "DSH ${state.runtime.dshVersion ?: "未知"} · Wire ${it.version} · ${modelLabel(state)}"
                    }
                        ?: state.hostError
                        ?: "127.0.0.1:3080",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = viewModel::startHost,
                        enabled = state.runtime.runtimeInstalled && !hostReady && !state.runtimeActionInProgress,
                    ) {
                        Text(
                            when {
                                state.runtimeActionInProgress -> "处理中…"
                                state.runtime.hostRunning -> "修复连接"
                                else -> "启动 Host"
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = viewModel::stopHost,
                        enabled = state.runtime.hostRunning && !state.runtimeActionInProgress,
                    ) {
                        Text("停止")
                    }
                    TextButton(onClick = viewModel::refresh, enabled = !state.loading) {
                        Text(if (state.loading) "检查中…" else "刷新")
                    }
                }
                state.runtimeActionMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            PanelSection("模型") {
                val hostReady = state.host != null
                StatusRow(
                    label = "DeepSeek API Key",
                    good = hostReady && state.deepSeekCredentialConfigured,
                    detail = when {
                        !hostReady -> "连接 Host 后检查"
                        state.deepSeekCredentialConfigured -> "已安全配置，App 不会读回明文"
                        else -> "尚未配置"
                    },
                )
                if (hostReady && !state.deepSeekCredentialConfigured) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("DEEPSEEK_API_KEY") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !state.credentialSaving,
                    )
                    Button(
                        onClick = {
                            viewModel.saveDeepSeekApiKey(apiKey)
                            apiKey = ""
                        },
                        enabled = apiKey.isNotBlank() && !state.credentialSaving,
                    ) {
                        Text(if (state.credentialSaving) "保存中…" else "保存 Key")
                    }
                }
                state.credentialMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.modelDirectory?.let { directory ->
                    HorizontalDivider()
                    Text("当前会话", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    directory.groups.forEach { group ->
                        Text(group.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            group.models.forEach { model ->
                                val selected = directory.current?.provider == group.id && directory.current.model == model.id
                                if (selected) {
                                    Button(onClick = {}, enabled = false) { Text(model.name) }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.selectModel(
                                                ModelSelection(group.id, model.id, model.defaultEffort),
                                            )
                                        },
                                        enabled = !state.modelSelectionInProgress && !state.promptSubmitting,
                                    ) { Text(model.name) }
                                }
                            }
                        }
                    }
                    val current = directory.current
                    val selectedModel = directory.groups
                        .firstOrNull { it.id == current?.provider }
                        ?.models
                        ?.firstOrNull { it.id == current?.model }
                    if (current != null && selectedModel != null && selectedModel.efforts.isNotEmpty()) {
                        Text("思考强度", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            selectedModel.efforts.forEach { effort ->
                                val selected = current.reasoningEffort == effort.id
                                if (selected) {
                                    Button(onClick = {}, enabled = false) { Text(effort.name) }
                                } else {
                                    OutlinedButton(
                                        onClick = { viewModel.selectModel(current.copy(reasoningEffort = effort.id)) },
                                        enabled = !state.modelSelectionInProgress && !state.promptSubmitting,
                                    ) { Text(effort.name) }
                                }
                            }
                        }
                    }
                }
                state.modelMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            SecurityNotice()
        }

        item {
            TextButton(
                onClick = { showDiagnostics = !showDiagnostics },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(if (showDiagnostics) "收起开发者详情" else "查看开发者详情")
            }
        }

        if (showDiagnostics) {
            item { RuntimeDiagnostics(state.runtime) }
            state.host?.let { host ->
                item {
                    PanelSection("Host 详情") {
                        DetailRow("工作目录", host.cwd)
                        DetailRow("默认模型", modelLabel(state))
                        DetailRow("活跃会话", host.attachedSessions.toString())
                        DetailRow(
                            "事件连接",
                            DshApiClient.Stream.entries.joinToString { stream ->
                                "${stream.name}:${if (stream in state.eventStreamsOpen) "open" else "closed"}"
                            },
                        )
                    }
                }
            }
            if (state.recentEvents.isNotEmpty()) {
                item { SectionLabel("最近事件") }
                items(state.recentEvents, key = { it.rpcId }) { event ->
                    EventRow(event.method, event.payload.toString(2))
                }
            }
        }
    }
}

@Composable
private fun PanelSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        SectionLabel(title)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RuntimeDiagnostics(runtime: RuntimeProbe) {
    PanelSection("本地运行环境") {
        StatusRow("Root (${runtime.rootProvider})", runtime.rootAvailable, runtime.rootDetail)
        StatusRow("Android", runtime.apiLevel >= 35, "${runtime.androidVersion} · API ${runtime.apiLevel}")
        StatusRow("设备", true, "${runtime.manufacturer} ${runtime.model} · ${runtime.deviceAbi}")
        StatusRow("SELinux", runtime.selinuxMode == "Enforcing", runtime.selinuxMode)
        StatusRow("Android native runtime", runtime.runtimeInstalled, runtime.runtimePath)
        StatusRow("Node.js (Bionic/arm64)", runtime.nodeInstalled, if (runtime.nodeInstalled) "已安装" else "尚未安装")
        StatusRow("DeepSeek Harness", runtime.harnessInstalled, if (runtime.harnessInstalled) "已安装" else "尚未安装")
        StatusRow("Android root plugin", runtime.androidPluginInstalled, if (runtime.androidPluginInstalled) "已安装" else "尚未安装")
    }
}

@Composable
private fun SecurityNotice() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Full-root 模式", fontWeight = FontWeight.Bold)
            Text(
                "DeepSeek Harness 以 uid 0 运行，能操作 Android 文件、应用与系统能力。Host 只接受本 APK 通过回环端口访问；需要立即中止时，可在上方停止 Host。当前会话不提供逐次审批。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, good: Boolean, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(9.dp)
                .background(if (good) ReadyGreen else MaterialTheme.colorScheme.outline, CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            modifier = Modifier.weight(0.32f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                value,
                modifier = Modifier.weight(0.68f),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun EventRow(method: String, body: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(method, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SelectionContainer {
                Text(
                    body,
                    maxLines = 8,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun modelLabel(state: HarnessUiState): String = state.host
    ?.let { listOfNotNull(it.provider, it.model).joinToString(" · ") }
    ?.ifBlank { "本机模型" }
    ?: "本机模型"

private fun modelDisplayName(state: HarnessUiState): String = when (state.host?.model) {
    "deepseek-v4-flash" -> "DeepSeek V4 Flash"
    null -> "本机模型"
    else -> state.host?.model.orEmpty()
}

private fun activeSessionTitle(state: HarnessUiState): String {
    val active = state.sessions.firstOrNull { it.sessionId == state.sessionId }
    return active?.title ?: if (active?.blank == true) "新对话" else "Smart Hole"
}
