package com.smartaodi.dshandroid.ui.features.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartaodi.dshandroid.HarnessUiState
import com.smartaodi.dshandroid.features.workspace.WorkspaceView
import com.smartaodi.dshandroid.protocol.SessionSummary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorkspaceSessionsPanel(
    state: HarnessUiState,
    onNewSession: () -> Unit,
    onNewSessionInWorkspace: (String) -> Unit,
    onOpenSession: (String) -> Unit,
    onRenameWorkspace: (String, String) -> Unit,
    onDeleteWorkspace: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onForkSession: (String) -> Unit,
    onArchiveSession: (String) -> Unit,
    onClose: () -> Unit,
) {
    var renameWorkspace by remember { mutableStateOf<WorkspaceView?>(null) }
    var deleteWorkspace by remember { mutableStateOf<WorkspaceView?>(null) }
    var renameSession by remember { mutableStateOf<SessionSummary?>(null) }
    val sessionsById = state.sessions.associateBy(SessionSummary::sessionId)
    val visibleSessions = state.workspaces.visibleRootSessions(state.sessions, state.sessionId)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 38.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("对话", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("对话记录保存在本机", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onClose) { Text("完成") }
            }
        }
        item {
            Button(
                onClick = onNewSession,
                enabled = !state.sessionLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) { Text("＋ 新对话") }
        }
        state.workspaces.error?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        state.workspaces.items.forEach { workspace ->
            item(key = "workspace-${workspace.workspaceId}") {
                WorkspaceHeader(
                    workspace,
                    onNewSessionInWorkspace,
                    { renameWorkspace = workspace },
                    { deleteWorkspace = workspace },
                )
            }
            val members = workspace.sessionIds.mapNotNull(sessionsById::get).filter { it in visibleSessions }
            if (members.isEmpty()) {
                item(key = "workspace-empty-${workspace.workspaceId}") { EmptyPanelText("这个工作区还没有对话") }
            } else {
                items(members, key = SessionSummary::sessionId) { session ->
                    ManagedSessionRow(
                        session,
                        state.sessionId == session.sessionId,
                        onOpenSession,
                        { renameSession = session },
                        onForkSession,
                        onArchiveSession,
                    )
                }
            }
        }
        val accounted = state.workspaces.items.flatMap(WorkspaceView::sessionIds).toSet()
        val ungrouped = visibleSessions.filter { it.sessionId !in accounted }
        if (ungrouped.isNotEmpty() || state.workspaces.items.isEmpty()) {
            item { PanelLabel(if (state.workspaces.items.isEmpty()) "最近对话" else "其他对话") }
            items(ungrouped, key = SessionSummary::sessionId) { session ->
                ManagedSessionRow(
                    session,
                    state.sessionId == session.sessionId,
                    onOpenSession,
                    { renameSession = session },
                    onForkSession,
                    onArchiveSession,
                )
            }
        }
        if (visibleSessions.isEmpty()) item { EmptyPanelText("还没有对话。创建一个新对话开始吧。") }
    }

    renameWorkspace?.let { workspace ->
        TextInputDialog(
            title = "重命名工作区",
            label = "工作区名称",
            initial = workspace.title,
            confirm = "保存",
            onDismiss = { renameWorkspace = null },
            onConfirm = {
                onRenameWorkspace(workspace.workspaceId, it)
                renameWorkspace = null
            },
        )
    }
    renameSession?.let { session ->
        TextInputDialog(
            title = "重命名对话",
            label = "对话标题",
            initial = session.title.orEmpty(),
            confirm = "保存",
            onDismiss = { renameSession = null },
            onConfirm = {
                onRenameSession(session.sessionId, it)
                renameSession = null
            },
        )
    }
    deleteWorkspace?.let { workspace ->
        AlertDialog(
            onDismissRequest = { deleteWorkspace = null },
            title = { Text("移除工作区？") },
            text = { Text("只移除“${workspace.title}”的工作区登记，不会删除目录、文件或对话。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteWorkspace(workspace.workspaceId)
                    deleteWorkspace = null
                }) { Text("移除") }
            },
            dismissButton = { TextButton(onClick = { deleteWorkspace = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun WorkspaceHeader(
    workspace: WorkspaceView,
    onNewSession: (String) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(workspace.title, fontWeight = FontWeight.Bold)
                    Text(workspace.path, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onNewSession(workspace.workspaceId) }) { Text("＋") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = onRename) { Text("重命名") }
                TextButton(onClick = onDelete) { Text("移除登记") }
            }
        }
    }
}

@Composable
private fun ManagedSessionRow(
    session: SessionSummary,
    selected: Boolean,
    onOpen: (String) -> Unit,
    onRename: () -> Unit,
    onFork: (String) -> Unit,
    onArchive: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).clickable { onOpen(session.sessionId) },
        shape = RoundedCornerShape(17.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(32.dp).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (session.blank) "＋" else "H", color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(session.title ?: if (session.blank) "新对话" else "未命名对话", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(session.cwd?.let { File(it).name }.takeUnless { it.isNullOrBlank() }, formatTime(session.updatedAt).takeIf(String::isNotBlank)).joinToString(" · "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (session.running) Box(Modifier.size(8.dp).background(ColorReady, CircleShape))
                else if (selected) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                TextButton(onClick = onRename) { Text("重命名") }
                TextButton(onClick = { onFork(session.sessionId) }) { Text("分叉") }
                TextButton(onClick = { onArchive(session.sessionId) }) { Text("归档") }
            }
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(value.trim()) }, enabled = value.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PanelLabel(text: String) {
    Text(text, modifier = Modifier.padding(top = 8.dp, start = 4.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun EmptyPanelText(text: String) {
    Text(text, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun formatTime(timestamp: Long): String = if (timestamp <= 0) "" else SimpleDateFormat("M月d日 HH:mm", Locale.getDefault()).format(Date(timestamp))

private val ColorReady = androidx.compose.ui.graphics.Color(0xFF18A66A)
