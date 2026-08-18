package com.smartaodi.dshandroid.ui.features.queue

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartaodi.dshandroid.features.queue.PromptMode
import com.smartaodi.dshandroid.features.queue.QueueFeatureState
import com.smartaodi.dshandroid.features.queue.QueueItem
import com.smartaodi.dshandroid.features.commands.CommandDescriptor
import com.smartaodi.dshandroid.features.commands.CommandsFeatureState
import com.smartaodi.dshandroid.features.ContextPressure
import com.smartaodi.dshandroid.features.TokenUsage
import com.smartaodi.dshandroid.features.status.ApiBalance
import java.math.BigDecimal

@Composable
fun QueueComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    ready: Boolean,
    submitting: Boolean,
    planMode: Boolean,
    planModeChanging: Boolean,
    contextPressure: ContextPressure?,
    tokenUsage: TokenUsage?,
    apiBalance: ApiBalance?,
    queue: QueueFeatureState,
    commands: CommandsFeatureState,
    onPlanModeChange: (Boolean) -> Unit,
    onSend: (PromptMode) -> Unit,
    onCancel: () -> Unit,
    onEditQueueItem: (String, String) -> Unit,
    onRemoveQueueItem: (String) -> Unit,
    onSteerQueueItem: (String) -> Unit,
) {
    var mode by remember(submitting) { mutableStateOf(PromptMode.Queue) }
    val commandCandidates = commands.matching(draft)
    val conversationStatus = conversationStatusLabel(contextPressure, tokenUsage, apiBalance)
    Column(
        modifier = Modifier
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (commandCandidates.isNotEmpty()) {
            CommandDock(commandCandidates) { command ->
                onDraftChange("/${command.name}${if (command.inputHint != null) " " else ""}")
            }
        }
        if (queue.queued.isNotEmpty()) {
            QueueDock(
                items = queue.queued,
                operations = queue.operationsInProgress,
                onEdit = onEditQueueItem,
                onRemove = onRemoveQueueItem,
                onSteer = onSteerQueueItem,
            )
        }
        if (queue.steering.isNotEmpty()) {
            Text(
                "正在插入当前回合：${queue.steering.joinToString { it.text }}",
                modifier = Modifier.padding(horizontal = 12.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlanModeButton(
                active = planMode,
                changing = planModeChanging,
                enabled = ready,
                onClick = { onPlanModeChange(!planMode) },
            )
            conversationStatus?.let { ConversationStatus(it) }
            if (submitting) {
                if (conversationStatus == null) Spacer(Modifier.weight(1f))
                PromptModeButton("排队", mode == PromptMode.Queue) { mode = PromptMode.Queue }
                PromptModeButton("插队", mode == PromptMode.Steer) { mode = PromptMode.Steer }
            }
        }
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        ) {
            Column {
                Row(
                    modifier = Modifier.padding(start = 18.dp, top = 6.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        enabled = ready,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        minLines = 1,
                        maxLines = 5,
                        decorationBox = { innerTextField ->
                            Box {
                                if (draft.isEmpty()) {
                                    Text(
                                        text = when {
                                            !ready -> "请先在系统面板完成准备"
                                            submitting && mode == PromptMode.Steer -> "发消息打断并引导当前回合"
                                            submitting -> "继续输入，消息将在当前回合后执行"
                                            planMode -> "描述任务，让 Smart Hole 先制定计划"
                                            else -> "给 Smart Hole 发消息"
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    if (submitting) {
                        StopButton(onCancel)
                        Spacer(Modifier.width(6.dp))
                    }
                    SendButton(canSend = ready && draft.isNotBlank()) { onSend(mode) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.ConversationStatus(label: String) {
    Spacer(Modifier.width(9.dp))
    Text(
        label,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal fun conversationStatusLabel(
    context: ContextPressure?,
    tokenUsage: TokenUsage?,
    apiBalance: ApiBalance?,
): String? {
    val labels = buildList {
        context?.let {
            val used = it.projectedTokens ?: it.pressureTokens
            if (used != null && it.contextWindow != null) {
                add("上下文 ${formatTokenCount(used)}/${formatTokenCount(it.contextWindow)}")
            } else if (used != null) {
                add("上下文 ${formatTokenCount(used)}")
            }
        }
        tokenUsage?.cacheHitPercent?.let { add("缓存命中 $it%") }
        apiBalance?.balances?.joinToString("/") { balance ->
            val symbol = when (balance.currency) {
                "CNY" -> "¥"
                "USD" -> "$"
                else -> "${balance.currency} "
            }
            val amount = balance.total.toBigDecimalOrNull()
                ?.stripTrailingZeros()
                ?.toPlainString()
                ?: balance.total
            "$symbol$amount"
        }?.takeIf(String::isNotBlank)?.let { add("余额 $it") }
    }
    return labels.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun formatTokenCount(value: Long): String = when {
    value >= 1_000_000 -> compactDecimal(value, 1_000_000, "M")
    value >= 1_000 -> compactDecimal(value, 1_000, "K")
    else -> value.toString()
}

private fun compactDecimal(value: Long, unit: Long, suffix: String): String {
    val scaled = BigDecimal.valueOf(value).divide(BigDecimal.valueOf(unit), 1, java.math.RoundingMode.HALF_UP)
    return scaled.stripTrailingZeros().toPlainString() + suffix
}

@Composable
private fun CommandDock(items: List<CommandDescriptor>, onPick: (CommandDescriptor) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            items.forEach { command ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPick(command) }.padding(horizontal = 13.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("/${command.name}", modifier = Modifier.width(112.dp), style = MaterialTheme.typography.labelLarge)
                    Column(Modifier.weight(1f)) {
                        Text(command.description, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        command.inputHint?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueDock(
    items: List<QueueItem>,
    operations: Set<String>,
    onEdit: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onSteer: (String) -> Unit,
) {
    var editing by remember { mutableStateOf<QueueItem?>(null) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text("等待队列 · ${items.size}", style = MaterialTheme.typography.labelLarge)
            items.forEach { item ->
                Column(Modifier.padding(top = 6.dp)) {
                    Text(item.text, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(onClick = { onSteer(item.id) }, enabled = item.id !in operations) { Text("现在执行") }
                        TextButton(onClick = { editing = item }, enabled = item.id !in operations) { Text("编辑") }
                        TextButton(onClick = { onRemove(item.id) }, enabled = item.id !in operations) { Text("移除") }
                    }
                }
            }
        }
    }
    editing?.let { item ->
        var value by remember(item.id) { mutableStateOf(item.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("编辑排队消息") },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    minLines = 2,
                    maxLines = 6,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEdit(item.id, value)
                        editing = null
                    },
                    enabled = value.isNotBlank(),
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun PromptModeButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PlanModeButton(
    active: Boolean,
    changing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val clickable = enabled && !changing
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = clickable, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Text(
            when {
                changing -> "Plan · 切换中"
                active -> "Plan ×"
                else -> "Plan"
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (clickable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun StopButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun SendButton(canSend: Boolean, onSend: () -> Unit) {
    Surface(
        modifier = Modifier.size(42.dp).clip(CircleShape).clickable(enabled = canSend, onClick = onSend),
        shape = CircleShape,
        color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(18.dp)) {
                val color = if (canSend) Color.White else Color.Gray
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.82f), androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.18f), 2.4.dp.toPx(), StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.44f), androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.18f), 2.4.dp.toPx(), StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.18f), androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.44f), 2.4.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}
