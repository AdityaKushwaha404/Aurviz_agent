package com.aurviz.agent.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
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
    val themeMode by viewModel.themeMode.collectAsState()
    val demoMode by viewModel.demoMode.collectAsState()
    val context = LocalContext.current

    var keyInput by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var emailInput by remember(currentEmail) { mutableStateOf(currentEmail) }
    var keyVisible by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // Re-check the account each time Settings opens so a one-off network error does not stick.
    LaunchedEffect(Unit) { viewModel.refreshGmailStatus() }
    LaunchedEffect(storageMessage) { storageMessage?.let { snackbar.showSnackbar(it) } }

    val isConnected = gmailConnectionState.startsWith("Connected as ")
    val hasProblem = gmailConnectionState.startsWith("Could not reach") ||
        gmailConnectionState.contains("expired", ignoreCase = true)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- Appearance ----
            SettingsSection(title = "Appearance", icon = Icons.Default.Palette) {
                Text(
                    "Choose how Aurviz looks. System default follows your phone's setting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val options = listOf(
                        Triple("SYSTEM", "System", Icons.Default.PhoneAndroid),
                        Triple("LIGHT", "Light", Icons.Default.LightMode),
                        Triple("DARK", "Dark", Icons.Default.DarkMode)
                    )
                    options.forEachIndexed { index, (value, label, icon) ->
                        SegmentedButton(
                            selected = themeMode == value,
                            onClick = { viewModel.setThemeMode(value) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        ) { Text(label) }
                    }
                }
            }

            // ---- Gmail ----
            SettingsSection(title = "Gmail", icon = Icons.Default.Email) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(
                        when {
                            isConnected -> SuccessGreen
                            hasProblem -> WarningOrange
                            else -> MaterialTheme.colorScheme.outline
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        gmailConnectionState,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    "Signs in with Google, so Aurviz never sees your password. It can search and read mail for " +
                        "summaries and save drafts. It sends only after you approve the recipient, subject and message.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Demo mailbox", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Use a built-in sample inbox (interviews, assignments, alerts). No Google account needed, " +
                                "and nothing is ever sent to a real address.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(checked = demoMode, onCheckedChange = { viewModel.setDemoMode(it) })
                }
                if (isConnected) {
                    OutlinedButton(onClick = onDisconnectGmail) { Text("Disconnect") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onConnectGmail) { Text("Connect Gmail") }
                        if (hasProblem) {
                            OutlinedButton(onClick = viewModel::refreshGmailStatus) { Text("Retry") }
                        }
                    }
                }
            }

            // ---- AI ----
            SettingsSection(title = "AI model", icon = Icons.Default.AutoAwesome) {
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("Gemini API key") },
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (keyVisible) "Hide key" else "Show key"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Text(
                    if (viewModel.usesBuiltInKey && keyInput.isBlank())
                        "Using the key bundled with this build. Enter your own to override it."
                    else "Stored in Android Keystore-backed encrypted storage and excluded from backups.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { viewModel.updateApiKey(keyInput) },
                    enabled = keyInput != currentApiKey && keyInput.isNotBlank()
                ) { Text("Save key") }
            }

            // ---- Preferences ----
            SettingsSection(title = "Preferences", icon = Icons.Default.Tune) {
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("Default recipient email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = { viewModel.updateDefaultEmail(emailInput) },
                    enabled = emailInput != currentEmail
                ) { Text("Save email") }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Spoken replies", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Read the agent's answers aloud.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = voiceFeedback, onCheckedChange = { viewModel.setVoiceFeedback(it) })
                }
            }

            // ---- Permissions ----
            SettingsSection(title = "Permissions", icon = Icons.Default.Security) {
                Text(
                    "Microphone is used only for voice commands. Notifications report scheduled task results.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open system permissions")
                }
            }

            // ---- Performance ----
            SettingsSection(title = "Performance", icon = Icons.Default.Speed) {
                PerformancePanel(performance, onClear = viewModel::clearPerformanceSamples)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
}

@Composable
private fun PerformancePanel(metrics: List<MetricSummary>, onClear: () -> Unit) {
    Text(
        "Latency measured on this device from real use. Agent turn includes network time to Gemini and Gmail.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (metrics.isEmpty()) {
        Text("No samples yet. Send a few commands to collect measurements.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Text("Metric", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(2.2f))
        listOf("n", "median", "p95", "ok").forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.9f))
        }
    }
    metrics.forEach { metric ->
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(metricLabel(metric.metric), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(2.2f))
            Text("${metric.count}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.9f))
            Text(formatMs(metric.medianMs), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.9f))
            Text(formatMs(metric.p95Ms), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.9f))
            Text("${(metric.successRate * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.9f))
        }
    }
    TextButton(onClick = onClear) { Text("Reset samples") }
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
