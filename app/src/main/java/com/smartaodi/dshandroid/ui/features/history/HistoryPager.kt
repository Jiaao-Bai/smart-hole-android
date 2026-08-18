package com.smartaodi.dshandroid.ui.features.history

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import com.smartaodi.dshandroid.features.history.HistoryFeatureState

@Composable
fun HistoryPager(state: HistoryFeatureState, onLoadOlder: () -> Unit) {
    if (!state.hasMore && state.error == null) return
    TextButton(
        onClick = onLoadOlder,
        enabled = state.hasMore && !state.loadingOlder,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            when {
                state.loadingOlder -> "正在加载更早的消息…"
                state.error != null -> "加载失败，点此重试"
                else -> "加载更早的消息"
            },
            color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}
