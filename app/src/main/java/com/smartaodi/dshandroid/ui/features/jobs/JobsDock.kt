package com.smartaodi.dshandroid.ui.features.jobs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartaodi.dshandroid.features.jobs.JobsFeatureState

@Composable
fun JobsDock(state: JobsFeatureState) {
    if (state.items.isEmpty()) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) {
            Text("后台任务${if (state.activeCount > 0) " · ${state.activeCount} 个运行中" else ""}", fontWeight = FontWeight.Bold)
            state.items.forEach { job ->
                Row(Modifier.padding(top = 5.dp)) {
                    Text(if (job.active) "●" else "○", color = if (job.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Column(Modifier.weight(1f)) {
                        Text(job.label.ifBlank { job.kind }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        Text(listOfNotNull(job.status, job.detail).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
