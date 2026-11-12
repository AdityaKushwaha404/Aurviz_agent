package com.aurviz.agent.presentation.tasks

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurviz.agent.core.util.TimeUtils
import com.aurviz.agent.domain.model.ScheduledTask
import com.aurviz.agent.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsState()
    val context = LocalContext.current
    var taskToEdit by remember { mutableStateOf<ScheduledTask?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scheduled Background Tasks", fontWeight = FontWeight.Bold) },
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
            Surface(
                color = PrimaryBlue.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "WorkManager runs tasks in the background when Android allows. Battery and idle policies can delay a run.",
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                }
            }

            Text(
                "${tasks.count { it.isEnabled }} active · ${tasks.count { !it.isEnabled }} completed",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(56.dp), tint = TextSecondary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No scheduled tasks yet.", fontWeight = FontWeight.SemiBold, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Say 'Every morning at 9 AM, check my emails and prepare a summary' to schedule one.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(tasks, key = { it.id }) { task ->
                        ScheduledTaskCard(
                            task = task,
                            onEdit = { taskToEdit = task },
                            onCancel = { viewModel.cancelTask(task.id) },
                            onTriggerNow = {
                                viewModel.triggerTaskNow(task.id) { result ->
                                    Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
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
        title = { Text("Edit scheduled task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Task name") }, singleLine = true)
                OutlinedTextField(action, { action = it }, label = { Text("Command") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(hour, { hour = it.filter(Char::isDigit).take(2) }, label = { Text("Hour (0–23)") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(minute, { minute = it.filter(Char::isDigit).take(2) }, label = { Text("Minute") }, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(task.copy(title = title.trim(), promptAction = action.trim(), hourOfDay = hour.toInt(), minute = minute.toInt()))
            }, enabled = title.isNotBlank() && action.isNotBlank() && (hour.toIntOrNull() ?: 24) in 0..23 && (minute.toIntOrNull() ?: 60) in 0..59) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ScheduledTaskCard(
    task: ScheduledTask,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onTriggerNow: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = SecondaryTeal.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(12.dp), tint = SecondaryTeal)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (task.isRecurring) "Daily at ${String.format("%02d:%02d", task.hourOfDay, task.minute)}" else "One-time",
                            color = SecondaryTeal,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit task", tint = PrimaryBlue)
                    }
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete task", tint = ErrorRed)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = task.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Action: ${task.promptAction}",
                fontSize = 13.sp,
                color = TextSecondary
            )

            var showDetails by remember(task.id) { mutableStateOf(false) }
            if (!task.lastExecutionStatus.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { showDetails = !showDetails }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (showDetails) "Hide last run" else "See last run", fontSize = 12.sp)
                    Icon(if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                if (showDetails) {
                    Surface(color = BackgroundLight, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(task.lastExecutionStatus.orEmpty(), fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (task.isEnabled) "Next run · ${TimeUtils.formatDateTime(task.nextRunEpoch)}" else "Not currently scheduled",
                fontSize = 12.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onTriggerNow, modifier = Modifier.align(Alignment.End), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Trigger now")
            }
        }
    }
}
