package com.aurviz.agent.presentation.tasks

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurviz.agent.core.util.TimeUtils
import com.aurviz.agent.domain.model.ExecutionLogFormatter
import com.aurviz.agent.domain.model.ScheduledTask
import com.aurviz.agent.presentation.components.MarkdownText
import androidx.compose.ui.unit.Dp
import com.aurviz.agent.domain.model.ScheduledTaskStatus
import com.aurviz.agent.domain.model.TaskExecutionRecord
import com.aurviz.agent.domain.model.TaskExecutionState
import androidx.compose.runtime.saveable.rememberSaveable
import com.aurviz.agent.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsState()
    val history by viewModel.history.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    var taskToEdit by remember { mutableStateOf<ScheduledTask?>(null) }
    var showInfoBanner by remember { mutableStateOf(true) }

    val activeCount = tasks.count { it.isEnabled }
    val runCount = history.size
    val completedCount = tasks.count {
        ScheduledTaskStatus.stateOf(it.lastExecutionStatus) == TaskExecutionState.SUCCESS
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tasks", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("Routines that run in the background", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Background Execution Info Banner
            AnimatedVisibility(visible = showInfoBanner) {
                Surface(
                    color = PrimaryBlue.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .border(1.dp, PrimaryBlue.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = PrimaryBlue,
                            shape = CircleShape,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Tasks keep running after you close the app. Android may start them a few minutes late to save battery.",
                            fontSize = 12.sp,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f),
                            lineHeight = 16.sp
                        )
                        IconButton(onClick = { showInfoBanner = false }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Stats Summary Pill Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = PrimaryBlue.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("$activeCount active", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                }
                Surface(
                    color = SuccessGreen.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = SuccessGreen)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("$completedCount succeeded", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                    }
                }
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Tasks (${tasks.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("History ($runCount)") }
                )
            }

            if (selectedTab == 1) {
                TaskHistoryList(history)
            } else if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryBlue.copy(alpha = 0.08f),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(40.dp), tint = PrimaryBlue)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No scheduled tasks yet", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Try saying 'Every morning at 9 AM, check my emails and prepare a summary'\nin the Agent tab to create one.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(tasks, key = { it.id }) { task ->
                        ScheduledTaskCard(
                            task = task,
                            onEdit = { taskToEdit = task },
                            onCancel = { viewModel.cancelTask(task.id) },
                            onToggleEnabled = { enabled -> viewModel.setTaskEnabled(task.id, enabled) },
                            onTriggerNow = {
                                viewModel.triggerTaskNow(task.id) { result ->
                                    Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(30.dp)) }
                }
            }
        }

        taskToEdit?.let { task ->
            EditScheduledTaskDialog(
                task = task,
                onDismiss = { taskToEdit = null },
                onSave = { updated -> viewModel.updateTask(updated); taskToEdit = null }
            )
        }
    }
}

@Composable
fun EditScheduledTaskDialog(
    task: ScheduledTask,
    onDismiss: () -> Unit,
    onSave: (ScheduledTask) -> Unit
) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var action by remember(task.id) { mutableStateOf(task.promptAction) }
    var hour by remember(task.id) { mutableStateOf(task.hourOfDay.toString()) }
    var minute by remember(task.id) { mutableStateOf(task.minute.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Scheduled Task", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Name") },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = action,
                    onValueChange = { action = it },
                    label = { Text("Autonomous Instruction") },
                    shape = RoundedCornerShape(14.dp),
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hour,
                        onValueChange = { hour = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hour (0–23)") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minute,
                        onValueChange = { minute = it.filter(Char::isDigit).take(2) },
                        label = { Text("Minute (0–59)") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(task.copy(title = title.trim(), promptAction = action.trim(), hourOfDay = hour.toInt(), minute = minute.toInt()))
                },
                enabled = title.isNotBlank() && action.isNotBlank() && (hour.toIntOrNull() ?: 24) in 0..23 && (minute.toIntOrNull() ?: 60) in 0..59,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) { Text("Save Changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ScheduledTaskCard(
    task: ScheduledTask,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onTriggerNow: () -> Unit
) {
    val isPaused = task.isRecurring && !task.isEnabled
    val executionState = ScheduledTaskStatus.stateOf(task.lastExecutionStatus)
    val isFailed = executionState == TaskExecutionState.FAILED

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = SecondaryTeal.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (task.isRecurring) Icons.Default.Repeat else Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = SecondaryTeal
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (task.isRecurring) "Daily at ${String.format("%02d:%02d", task.hourOfDay, task.minute)}" else "One-time Execution",
                            color = SecondaryTeal,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = PrimaryBlue.copy(alpha = 0.08f),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(onClick = onEdit)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit task", tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = ErrorRed.copy(alpha = 0.08f),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(onClick = onCancel)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete task", tint = ErrorRed, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = task.title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = task.promptAction,
                fontSize = 14.sp,
                color = TextSecondary,
                lineHeight = 20.sp
            )

            if (executionState != TaskExecutionState.NONE) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExecutionStatusChip(executionState)
                    Spacer(modifier = Modifier.weight(1f))
                    task.lastExecutionEpoch?.let { epoch ->
                        Text(
                            text = "Last run · ${TimeUtils.formatDateTime(epoch)}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            var showDetails by remember(task.id) { mutableStateOf(false) }
            if (!task.lastExecutionStatus.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDetails = !showDetails }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = if (isFailed) ErrorRed else PrimaryBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (showDetails) "Hide last result" else "View last result",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isFailed) ErrorRed else PrimaryBlue
                    )
                }
                if (showDetails) {
                    ExecutionLogContent(
                        state = executionState,
                        message = ScheduledTaskStatus.messageOf(task.lastExecutionStatus)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextSecondary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when {
                            isPaused -> "Paused"
                            isFailed -> "Last run failed"
                            task.isEnabled -> "Next · ${TimeUtils.formatDateTime(task.nextRunEpoch)}"
                            executionState == TaskExecutionState.SUCCESS && task.lastExecutionEpoch != null ->
                                "Completed · ${TimeUtils.formatDateTime(task.lastExecutionEpoch)}"
                            else -> "Task completed"
                        },
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                if (task.isRecurring) {
                    IconButton(onClick = { onToggleEnabled(isPaused) }) {
                        Icon(
                            if (isPaused) Icons.Default.PlayCircle else Icons.Default.PauseCircle,
                            contentDescription = if (isPaused) "Resume task" else "Pause task",
                            tint = if (isPaused) SuccessGreen else WarningOrange
                        )
                    }
                }

                Button(
                    onClick = onTriggerNow,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFailed) ErrorRed else PrimaryBlue
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        if (isFailed) Icons.Default.Refresh else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (isFailed) "Retry" else "Run now",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ExecutionStatusChip(state: TaskExecutionState) {
    val (label, color, icon) = when (state) {
        TaskExecutionState.SUCCESS -> Triple("Success", SuccessGreen, Icons.Default.CheckCircle)
        TaskExecutionState.FAILED -> Triple("Failed", ErrorRed, Icons.Default.ErrorOutline)
        TaskExecutionState.RETRYING -> Triple("Retrying", WarningOrange, Icons.Default.Sync)
        TaskExecutionState.SKIPPED -> Triple("Skipped", TextSecondary, Icons.Default.Info)
        TaskExecutionState.NONE -> return
    }

    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Readable execution history timeline across all scheduled tasks (FR-13). */
@Composable
private fun TaskHistoryList(history: List<TaskExecutionRecord>) {
    if (history.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                "No task runs yet. Runs appear here after a scheduled or manual execution.",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(history, key = { it.id }) { record ->
            var expanded by remember(record.id) { mutableStateOf(false) }
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
                    .clickable { expanded = !expanded }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            record.taskTitle,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        ExecutionStatusChip(record.state)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        relativeTime(record.startedAt) + " · took " + formatDuration(record.durationMs) +
                            if (record.attempt > 1) " · attempt ${record.attempt}" else "",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    if (record.message.isNotBlank() || record.state != TaskExecutionState.SUCCESS) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ExecutionLogContent(
                            state = record.state,
                            message = record.message,
                            collapsedLines = if (expanded) Int.MAX_VALUE else 4
                        )
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(30.dp)) }
    }
}

private fun formatDuration(ms: Long): String =
    if (ms < 1000) "${ms} ms" else String.format(java.util.Locale.US, "%.1f s", ms / 1000.0)


/**
 * Human-readable result of one run: a headline, a plain explanation (Markdown for
 * successful summaries), a suggested next step, and the raw technical text on demand.
 */
@Composable
private fun ExecutionLogContent(
    state: TaskExecutionState,
    message: String,
    collapsedLines: Int = Int.MAX_VALUE
) {
    val log = remember(state, message) { ExecutionLogFormatter.format(state, message) }
    val isProblem = state == TaskExecutionState.FAILED || state == TaskExecutionState.RETRYING
    val accent = when (state) {
        TaskExecutionState.FAILED -> ErrorRed
        TaskExecutionState.RETRYING -> WarningOrange
        TaskExecutionState.SUCCESS -> SuccessGreen
        else -> TextSecondary
    }
    var showTechnical by remember(message) { mutableStateOf(false) }

    Surface(
        color = accent.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                log.headline,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            if (state == TaskExecutionState.SUCCESS) {
                Box(modifier = Modifier.heightIn(max = if (collapsedLines == Int.MAX_VALUE) Dp.Unspecified else 96.dp)) {
                    MarkdownText(markdown = log.body, color = TextPrimary)
                }
            } else {
                Text(log.body, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
            log.hint?.let {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            if (isProblem && log.technical != null) {
                Text(
                    if (showTechnical) "Hide technical details" else "Technical details",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryBlue,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { showTechnical = !showTechnical }
                )
                if (showTechnical) {
                    Text(
                        log.technical,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

/** "Today, 11:17", "Yesterday, 09:00" or a short date, so history reads naturally. */
private fun relativeTime(epochMillis: Long): String {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
    val time = TimeUtils.formatTime(epochMillis)
    val sameDay = { a: java.util.Calendar, b: java.util.Calendar ->
        a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
            a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
    }
    return when {
        sameDay(now, then) -> "Today, $time"
        sameDay(now.apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }, then) -> "Yesterday, $time"
        else -> TimeUtils.formatDateTime(epochMillis)
    }
}
