package com.aurviz.agent.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurviz.agent.presentation.theme.*
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import com.aurviz.agent.domain.metrics.MetricSummary
import com.aurviz.agent.domain.metrics.PerformanceRecorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onConnectGmail: () -> Unit,
    onDisconnectGmail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gmailConnectionState by viewModel.gmailConnectionState.collectAsState()
    val currentApiKey by viewModel.apiKey.collectAsState()
    val currentEmail by viewModel.defaultEmail.collectAsState()
    val voiceFeedback by viewModel.voiceFeedback.collectAsState()
    val storageMessage by viewModel.storageMessage.collectAsState()
    val performance by viewModel.performance.collectAsState()
    val context = LocalContext.current

    var keyInput by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var emailInput by remember(currentEmail) { mutableStateOf(currentEmail) }
    var keyVisible by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    // Re-check the account each time Settings opens so a one-off network error does not stick.
    LaunchedEffect(Unit) { viewModel.refreshGmailStatus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agent Settings & Security", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Security & Privacy Card
            Card(
                colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Keystore-Backed Encrypted Storage", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "API keys and email addresses use encrypted preferences and are excluded from cloud backup and device transfer.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            // Gemini API Key Section
            Text("AI Developer API", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("Google Gemini API Key") },
                placeholder = { Text("AIzaSy...") },
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle visibility"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = {
                    viewModel.updateApiKey(keyInput)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Save API Key")
            }
            storageMessage?.let { message ->
                Text(message, color = if (message.contains("saved in encrypted storage")) SuccessGreen else ErrorRed)
            }

            HorizontalDivider()

            // Gmail account
            Text("Gmail account", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(gmailConnectionState, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Connected via Google OAuth; Aurviz never sees your password. Aurviz can search and read mail for summaries, " +
                            "save drafts, and sends only after you approve the recipient, subject, and message. " +
                            "If you connected before inbox access was added, disconnect and connect again.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    if (gmailConnectionState.startsWith("Could not reach")) {
                        OutlinedButton(onClick = viewModel::refreshGmailStatus) { Text("Retry") }
                    }
                    if (gmailConnectionState.startsWith("Connected as ")) {
                        OutlinedButton(onClick = onDisconnectGmail) {
                            Text("Disconnect Gmail")
                        }
                    } else {
                        Button(onClick = onConnectGmail) {
                            Text("Connect Gmail")
                        }
                    }
                }
            }

            // Tool Configuration Section
            Text("Tool Configuration", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            OutlinedTextField(
                value = emailInput,
                onValueChange = { emailInput = it },
                label = { Text("Default recipient email") },
                placeholder = { Text("recipient@company.com") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = {
                    viewModel.updateDefaultEmail(emailInput)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Save Email")
            }

            HorizontalDivider()

            // Voice Settings
            Text("Voice Interaction", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Spoken Agent Feedback (TTS)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Speak agent responses automatically after execution", fontSize = 12.sp, color = TextSecondary)
                }
                Switch(
                    checked = voiceFeedback,
                    onCheckedChange = { viewModel.setVoiceFeedback(it) }
                )
            }

            HorizontalDivider()

            // App permissions
            Text("App permissions", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "Microphone is used only for voice commands. Notifications report scheduled task results.",
                fontSize = 12.sp,
                color = TextSecondary
            )
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Manage permissions")
            }

            HorizontalDivider()

            PerformancePanel(performance, onClear = viewModel::clearPerformanceSamples)
        }
    }
}

@Composable
private fun PerformancePanel(metrics: List<MetricSummary>, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Performance (measured on this device)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "Latency from real usage. Agent turn includes network time to Gemini and Gmail.",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
        if (metrics.isNotEmpty()) TextButton(onClick = onClear) { Text("Reset") }
    }
    if (metrics.isEmpty()) {
        Text("No samples yet. Send a few commands to collect measurements.", fontSize = 13.sp, color = TextSecondary)
        return
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Metric", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.weight(2.2f))
                listOf("n", "median", "p95", "ok").forEach {
                    Text(it, fontSize = 11.sp, color = TextSecondary, modifier = Modifier.weight(0.8f))
                }
            }
            metrics.forEach { metric ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(metricLabel(metric.metric), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(2.2f))
                    Text("${metric.count}", fontSize = 12.sp, modifier = Modifier.weight(0.8f))
                    Text(formatMs(metric.medianMs), fontSize = 12.sp, modifier = Modifier.weight(0.8f))
                    Text(formatMs(metric.p95Ms), fontSize = 12.sp, modifier = Modifier.weight(0.8f))
                    Text("${(metric.successRate * 100).toInt()}%", fontSize = 12.sp, modifier = Modifier.weight(0.8f))
                }
            }
        }
    }
}

private fun metricLabel(metric: String): String = when (metric) {
    PerformanceRecorder.AGENT_TURN -> "Agent turn (end-to-end)"
    PerformanceRecorder.LLM_CALL -> "Gemini call"
    PerformanceRecorder.MEMORY_RETRIEVAL -> "Memory retrieval"
    PerformanceRecorder.SCHEDULED_RUN -> "Scheduled task run"
    else -> metric.removePrefix("tool:").let { "Tool: $it" }
}

private fun formatMs(ms: Long): String =
    if (ms < 1000) "$ms ms" else String.format(java.util.Locale.US, "%.1f s", ms / 1000.0)
