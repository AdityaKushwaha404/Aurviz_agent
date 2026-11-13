package com.aurviz.agent.presentation.agent

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.res.painterResource
import com.aurviz.agent.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurviz.agent.domain.model.AgentMessage
import com.aurviz.agent.domain.model.SensitiveAction
import com.aurviz.agent.domain.model.ToolStatus
import com.aurviz.agent.presentation.theme.*

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import android.widget.Toast
import com.aurviz.agent.core.util.TimeUtils
import com.aurviz.agent.presentation.components.AurvizLogo
import com.aurviz.agent.presentation.components.MarkdownText

private data class StarterPrompt(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val prompt: String
)

private val STARTER_PROMPTS = listOf(
    StarterPrompt(
        Icons.Default.MarkEmailRead, "Summarize my inbox",
        "Find important emails from the last few days.",
        "Check my important emails from the last 3 days and summarize them."
    ),
    StarterPrompt(
        Icons.Default.Schedule, "Daily 9 AM digest",
        "Run in the background every morning.",
        "Every morning at 9 AM, check my important emails and summarize them."
    ),
    StarterPrompt(
        Icons.Default.Drafts, "Draft an email",
        "Saved to Gmail Drafts, never sent without approval.",
        "Draft an email to my professor asking for a deadline extension. Don't send it."
    ),
    StarterPrompt(
        Icons.Default.Psychology, "Remember a preference",
        "Aurviz applies it to later replies.",
        "Remember that I prefer formal emails."
    ),
    StarterPrompt(
        Icons.Default.Language, "Browse the web",
        "Open a site and extract what matters.",
        "Open news.ycombinator.com and tell me the top 3 stories."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
    viewModel: AgentViewModel,
    modifier: Modifier = Modifier,
    onOpenBrowser: () -> Unit = {},
    onOpenMailbox: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val messages by viewModel.messages.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val recognizedText by viewModel.recognizedText.collectAsState()
    val rmsLevel by viewModel.rmsLevel.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val mailSource by viewModel.mailSource.collectAsState()
    val gmailState by (viewModel.gmailConnectionState ?: kotlinx.coroutines.flow.MutableStateFlow("")).collectAsState()
    val needsSignIn = mailSource != "DEMO" && !gmailState.startsWith("Connected as") && !gmailState.startsWith("Checking")

    var textInput by rememberSaveable { mutableStateOf("") }
    var showClearChatConfirmation by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // What the agent is doing right now, shown in the header and typing indicator.
    val activity = remember(messages, isProcessing, pendingConfirmation) {
        if (!isProcessing) null
        else if (pendingConfirmation != null) "Waiting for your approval"
        else (messages.lastOrNull { it is AgentMessage.ToolAction } as? AgentMessage.ToolAction)
            ?.takeIf { it.status == ToolStatus.RUNNING || it.status == ToolStatus.CONFIRMED }
            ?.let { ToolPresentation.runningLabel(it.toolName) }
            ?: "Thinking…"
    }

    // Keep the newest content in view, including the typing indicator.
    LaunchedEffect(messages.size, isProcessing) {
        val lastIndex = messages.size - 1 + if (isProcessing) 1 else 0
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
                ) {
                    AurvizLogo(size = 40.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Aurviz",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(if (isProcessing) WarningOrange else SuccessGreen, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                when {
                                    activity != null -> activity
                                    mailSource == "DEMO" -> "Demo mailbox"
                                    mailSource == "TEST" -> "Test account"
                                    else -> "Ready"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    // Real accounts are viewed in Gmail itself; only the demo mailbox has an in-app viewer.
                    if (mailSource == "DEMO") {
                        IconButton(onClick = onOpenMailbox) {
                            Icon(Icons.Default.Inbox, contentDescription = "Open demo mailbox", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = onOpenBrowser) {
                        Icon(Icons.Default.Language, contentDescription = "Open browser", tint = MaterialTheme.colorScheme.primary)
                    }
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { showClearChatConfirmation = true }, enabled = !isProcessing) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Clear conversation", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        bottomBar = {
            Composer(
                value = textInput,
                onValueChange = { textInput = it },
                isListening = isListening,
                isProcessing = isProcessing,
                onMic = { if (isListening) viewModel.stopVoiceInput() else viewModel.startVoiceInput() },
                onSend = {
                    if (textInput.isNotBlank()) {
                        viewModel.sendPrompt(textInput)
                        textInput = ""
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = modifier.fillMaxSize().padding(paddingValues)) {
        if (needsSignIn) {
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        if (mailSource == "TEST") "Email features need a sign-in. The test account details are in Settings."
                        else "Sign in to Gmail to use email features, or switch to the demo mailbox.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onOpenSettings) { Text("Open steps") }
                }
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty()) {
                EmptyState(onPick = { textInput = it })
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageItem(
                            message = message,
                            onRetry = if (isProcessing) null else { prompt -> viewModel.sendPrompt(prompt) }
                        )
                    }
                    if (isProcessing) {
                        item(key = "typing") { ProcessingIndicator(activity ?: "Thinking…") }
                    }
                }
            }

            // Sensitive Action Confirmation Dialog (Human-in-the-Loop with Interactive Editor)
            pendingConfirmation?.let { action ->
                ConfirmationDialog(
                    action = action,
                    onConfirm = { editedArguments -> viewModel.onConfirmAction(true, editedArguments) },
                    onDismiss = { viewModel.onConfirmAction(false) }
                )
            }

            if (showClearChatConfirmation) {
                AlertDialog(
                    onDismissRequest = { showClearChatConfirmation = false },
                    title = { Text("Clear conversation?") },
                    text = { Text("This removes the saved chat history from this device. Saved memories are kept.") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.clearConversation()
                            showClearChatConfirmation = false
                        }) { Text("Clear") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearChatConfirmation = false }) { Text("Cancel") }
                    }
                )
            }

            // Gemini / ChatGPT Voice Listening Animated Overlay
            GeminiVoiceListeningOverlay(
                isListening = isListening,
                recognizedText = recognizedText,
                rmsLevel = rmsLevel,
                onStop = { viewModel.stopVoiceInput() },
                onCancel = { viewModel.stopVoiceInput() },
                isSpeaking = isSpeaking,
                onStopSpeaking = { viewModel.stopSpeaking() },
                modifier = Modifier.fillMaxSize()
            )
        }
        }
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    isListening: Boolean,
    isProcessing: Boolean,
    onMic: () -> Unit,
    onSend: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            FilledIconButton(
                onClick = onMic,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isListening) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (isListening) MaterialTheme.colorScheme.onError
                    else MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (isListening) "Stop listening" else "Start voice input"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(if (isListening) "Listening…" else "Ask Aurviz anything", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(
                onClick = onSend,
                enabled = value.isNotBlank() && !isProcessing,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.outline
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
            }
        }
    }
}

@Composable
private fun EmptyState(onPick: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "How can I help?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Type or tap the mic. I can read your email, remember things, run tasks in the background and browse the web.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        items(STARTER_PROMPTS) { starter ->
            Surface(
                onClick = { onPick(starter.prompt) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                starter.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            starter.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            starter.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageItem(message: AgentMessage, onRetry: ((String) -> Unit)? = null) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    when (message) {
        is AgentMessage.UserPrompt -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.widthIn(max = 320.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        if (message.isVoiceInput) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Mic, contentDescription = "Voice message", modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Voice", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        Text(message.text, fontSize = 15.sp, lineHeight = 21.sp)
                    }
                }
            }
        }

        is AgentMessage.ToolAction -> ToolActionCard(message)

        is AgentMessage.AgentResponse -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.widthIn(max = 340.dp)
                ) {
                    MarkdownText(
                        markdown = message.text,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(message.text))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        TimeUtils.formatTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        is AgentMessage.ErrorMessage -> {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            message.error,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    val retryPrompt = message.retryPrompt
                    if (retryPrompt != null && onRetry != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onRetry(retryPrompt) },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Try again")
                        }
                    }
                }
            }
        }

        is AgentMessage.AgentReasoning -> {
            Text(
                message.thought,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic
            )
        }
    }
}

@Composable
private fun ToolActionCard(message: AgentMessage.ToolAction) {
    var expanded by remember { mutableStateOf(false) }
    val hasOutput = !message.resultOutput.isNullOrBlank()
    val longOutput = (message.resultOutput?.length ?: 0) > 140

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = longOutput) { expanded = !expanded }
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    ToolPresentation.icon(message.toolName),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    ToolPresentation.title(message.toolName),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (message.status == ToolStatus.RUNNING) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                StatusBadge(message.status)
            }
            if (hasOutput) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = message.resultOutput.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (longOutput) {
                    Text(
                        if (expanded) "Show less" else "Show more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: ToolStatus) {
    val (color, text) = when (status) {
        ToolStatus.RUNNING -> Pair(WarningOrange, "Running")
        ToolStatus.AWAITING_CONFIRMATION -> Pair(WarningOrange, "Needs approval")
        ToolStatus.CONFIRMED -> Pair(MaterialTheme.colorScheme.primary, "Approved")
        ToolStatus.CANCELLED -> Pair(MaterialTheme.colorScheme.onSurfaceVariant, "Cancelled")
        ToolStatus.SUCCESS -> Pair(SuccessGreen, "Done")
        ToolStatus.FAILED -> Pair(MaterialTheme.colorScheme.error, "Failed")
    }

    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun ProcessingIndicator(label: String = "Thinking…") {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 150),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(7.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), CircleShape)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ConfirmationDialog(
    action: SensitiveAction,
    onConfirm: (Map<String, Any?>?) -> Unit,
    onDismiss: () -> Unit
) {
    var editableRecipient by remember(action.actionId) { mutableStateOf((action as? SensitiveAction.SendEmail)?.recipient.orEmpty()) }
    var editableSubject by remember(action.actionId) { mutableStateOf((action as? SensitiveAction.SendEmail)?.subject.orEmpty()) }
    var editableBody by remember(action.actionId) { mutableStateOf((action as? SensitiveAction.SendEmail)?.body.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Security, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(32.dp))
        },
        title = {
            Text(
                when (action) {
                    is SensitiveAction.SendEmail -> "Review email before sending"
                    is SensitiveAction.BrowserInteraction -> "Review browser interaction"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when (action) {
                        is SensitiveAction.SendEmail -> "Approving sends this message from the connected Gmail account. Review every field before continuing."
                        is SensitiveAction.BrowserInteraction -> "The agent will interact with the current website. Review the target and data before continuing."
                    },
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                when (action) {
                    is SensitiveAction.SendEmail -> {
                        Surface(
                            color = BackgroundLight,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = editableRecipient,
                                    onValueChange = { editableRecipient = it },
                                    label = { Text("To") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editableSubject,
                                    onValueChange = { editableSubject = it },
                                    label = { Text("Subject") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editableBody,
                                    onValueChange = { editableBody = it },
                                    label = { Text("Body") },
                                    maxLines = 4,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    is SensitiveAction.BrowserInteraction -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(action.description, fontWeight = FontWeight.SemiBold)
                            Text("Page: ${action.pageTitle}", color = TextSecondary)
                            Text(action.pageUrl, color = TextSecondary)
                            Text("Selector: ${action.selector}", color = TextSecondary)
                            action.text?.let { Text("Text to enter: $it", color = TextPrimary) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val editedArguments: Map<String, Any?> = when (action) {
                        is SensitiveAction.SendEmail -> mapOf(
                            "recipient" to editableRecipient,
                            "subject" to editableSubject,
                            "body" to editableBody
                        )
                        is SensitiveAction.BrowserInteraction -> buildMap {
                            put("selector", action.selector)
                            action.text?.let { put("text", it) }
                        }
                    }
                    onConfirm(editedArguments)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text(
                    when (action) {
                        is SensitiveAction.SendEmail -> "Send email"
                        is SensitiveAction.BrowserInteraction -> "Approve action"
                    }
                )
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun GeminiVoiceListeningOverlay(
    isListening: Boolean,
    recognizedText: String?,
    rmsLevel: Float,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    isSpeaking: Boolean = false,
    onStopSpeaking: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isListening,
        enter = fadeIn(animationSpec = tween(250)) + slideInVertically(
            animationSpec = tween(300),
            initialOffsetY = { it }
        ),
        exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(
            animationSpec = tween(250),
            targetOffsetY = { it }
        ),
        modifier = modifier
    ) {
        val accent = MaterialTheme.colorScheme.primary
        val accentSoft = MaterialTheme.colorScheme.primaryContainer
        val infiniteTransition = rememberInfiniteTransition(label = "AurvizVoiceTransition")

        val pulseScale1 by infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse1"
        )
        val pulseScale2 by infiniteTransition.animateFloat(
            initialValue = 1.15f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse2"
        )
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowAlpha"
        )

        // Backdrop Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TextPrimary.copy(alpha = 0.38f))
                .clickable(enabled = false) {}
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.colorScheme.surface, // Aurviz theme surface
                tonalElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Drag Handle
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(4.dp)
                            .background(TextSecondary.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Status Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(accent, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSpeaking) "AURVIZ IS SPEAKING" else "LISTENING...",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            fontSize = 12.sp,
                            color = accent
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Gemini / ChatGPT Fluid Multi-Gradient Orb
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(160.dp)
                    ) {
                        // Outer Pulsing Glow (Electric Magenta / Violet)
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .scale(pulseScale1 + (rmsLevel * 0.03f).coerceAtMost(0.3f))
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            accent.copy(alpha = glowAlpha * 0.35f),
                                            accentSoft.copy(alpha = 0.28f),
                                            Color.Transparent
                                        )
                                    ),
                                    CircleShape
                                )
                        )

                        // Middle Pulsing Orb (Cyan / Electric Blue)
                        Box(
                            modifier = Modifier
                                .size(115.dp)
                                .scale(pulseScale2 + (rmsLevel * 0.04f).coerceAtMost(0.4f))
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            accent.copy(alpha = glowAlpha * 0.55f),
                                            accentSoft.copy(alpha = 0.55f),
                                            Color.Transparent
                                        )
                                    ),
                                    CircleShape
                                )
                        )

                        // Central Dynamic Equalizer Wave Bars
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(48.dp)
                        ) {
                            val barFactors = listOf(0.4f, 0.8f, 1.0f, 0.7f, 0.5f)
                            barFactors.forEachIndexed { _, factor ->
                                val dynamicHeight = (12.dp + ((rmsLevel * 4f + pulseScale1 * 20f) * factor).dp).coerceIn(10.dp, 44.dp)
                                Box(
                                    modifier = Modifier
                                        .width(5.dp)
                                        .height(dynamicHeight)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.White, accent)
                                            ),
                                            RoundedCornerShape(3.dp)
                                        )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Live Speech Transcription Subtitle Box
                    Surface(
                        color = accent.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = if (!recognizedText.isNullOrBlank()) "\"$recognizedText\"" else "Speak your request naturally...",
                            color = if (!recognizedText.isNullOrBlank()) TextPrimary else TextSecondary,
                            fontSize = 15.sp,
                            fontWeight = if (!recognizedText.isNullOrBlank()) FontWeight.Medium else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (isSpeaking) {
                        Button(onClick = onStopSpeaking, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Stop speaking")
                        }
                    }

                    // Controls: Stop and Cancel
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cancel Button
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .size(48.dp)
                                .background(ToolBadgeBackground, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel",
                                tint = TextSecondary
                            )
                        }

                        // Primary Stop & Send Button
                        Button(
                            onClick = onStop,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryBlue
                            ),
                            shape = CircleShape,
                            modifier = Modifier
                                .height(56.dp)
                                .padding(horizontal = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Done Speaking",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}
