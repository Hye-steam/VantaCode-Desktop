package com.vantacode.feature.schedule.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vantacode.R
import com.vantacode.feature.schedule.data.entity.ScheduledTaskEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 定时任务列表。
 *
 * 页面顶部固定写明执行精度的限制：WorkManager 的周期下限是 15 分钟且会被系统省电策略延后，
 * 不写清楚用户会以为「间隔 60 分钟」等于准点执行。
 */
@Composable
internal fun ScheduledTasksSection(
    tasks: List<ScheduledTaskEntity>,
    onSave: (Long?, String, String, Int, String?) -> Unit,
    onToggle: (ScheduledTaskEntity, Boolean) -> Unit,
    onDelete: (ScheduledTaskEntity) -> Unit,
    onRunNow: (ScheduledTaskEntity) -> Unit
) {
    var editing by remember { mutableStateOf<ScheduledTaskEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_scheduled_tasks),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.schedule_precision_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { creating = true }) {
                Text(stringResource(R.string.schedule_new))
            }
        }

        if (tasks.isEmpty()) {
            Text(
                text = stringResource(R.string.schedule_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onToggle = { onToggle(task, it) },
                        onEdit = { editing = task },
                        onDelete = { onDelete(task) },
                        onRunNow = { onRunNow(task) }
                    )
                }
            }
        }
    }

    if (creating) {
        TaskEditorDialog(
            task = null,
            onDismiss = { creating = false },
            onConfirm = { title, prompt, interval, root ->
                onSave(null, title, prompt, interval, root)
                creating = false
            }
        )
    }

    editing?.let { task ->
        TaskEditorDialog(
            task = task,
            onDismiss = { editing = null },
            onConfirm = { title, prompt, interval, root ->
                onSave(task.id, title, prompt, interval, root)
                editing = null
            }
        )
    }
}

@Composable
private fun TaskCard(
    task: ScheduledTaskEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRunNow: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.schedule_every_minutes, task.intervalMinutes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = task.enabled, onCheckedChange = onToggle)
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = task.prompt,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )

            Spacer(Modifier.height(8.dp))
            val statusColor = when (task.lastStatus) {
                "success" -> MaterialTheme.colorScheme.primary
                "error" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = buildString {
                    if (task.enabled) {
                        append(stringResource(R.string.schedule_next_run, formatter.format(Date(task.nextRunAt))))
                    } else {
                        append(stringResource(R.string.schedule_disabled))
                    }
                    task.lastMessage?.takeIf { it.isNotBlank() }?.let {
                        append(" · ")
                        append(it.take(60))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
                maxLines = 2
            )

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRunNow) { Text(stringResource(R.string.schedule_run_now)) }
                TextButton(onClick = onEdit) { Text(stringResource(R.string.common_edit)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.common_delete)) }
            }
        }
    }
}

@Composable
private fun TaskEditorDialog(
    task: ScheduledTaskEntity?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, String?) -> Unit
) {
    var title by remember { mutableStateOf(task?.title.orEmpty()) }
    var prompt by remember { mutableStateOf(task?.prompt.orEmpty()) }
    var interval by remember {
        mutableStateOf(task?.intervalMinutes?.toString() ?: "60")
    }
    var projectRoot by remember { mutableStateOf(task?.projectRoot.orEmpty()) }

    val intervalValue = interval.toIntOrNull() ?: 0
    val valid = title.isNotBlank() && prompt.isNotBlank() &&
        intervalValue >= ScheduledTaskEntity.MIN_INTERVAL_MINUTES

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (task == null) R.string.schedule_new else R.string.schedule_edit
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.schedule_field_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.schedule_field_prompt)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = interval,
                    onValueChange = { input -> interval = input.filter { it.isDigit() }.take(5) },
                    label = { Text(stringResource(R.string.schedule_field_interval)) },
                    singleLine = true,
                    isError = intervalValue in 1 until ScheduledTaskEntity.MIN_INTERVAL_MINUTES,
                    supportingText = {
                        Text(stringResource(R.string.schedule_interval_min_hint))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = projectRoot,
                    onValueChange = { projectRoot = it },
                    label = { Text(stringResource(R.string.schedule_field_project)) },
                    singleLine = true,
                    supportingText = {
                        Text(stringResource(R.string.schedule_project_hint))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onConfirm(
                        title,
                        prompt,
                        intervalValue.coerceAtLeast(ScheduledTaskEntity.MIN_INTERVAL_MINUTES),
                        projectRoot.takeIf { it.isNotBlank() }
                    )
                }
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
