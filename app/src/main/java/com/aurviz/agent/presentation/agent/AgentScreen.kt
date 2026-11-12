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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
    viewModel: AgentViewModel,
    modifier: Modifier = Modifier,
    onOpenBrowser: () -> Unit = {}
) {
    val messages by viewModel.messages.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val recognizedText by viewModel.recognizedText.collectAsState()
    val rmsLevel by viewModel.rmsLevel.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()

    var textInput by remember { mutableStateOf("") }
    var showClearChatConfirmation by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new message
    LaunchedEffect(messages.size, isProcessing) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = PrimaryBlue,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.padding(5.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.Center) {
                            Text("Aurviz", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isProcessing) WarningOrange else SuccessGreen,
                                    modifier = Modifier.size(7.dp)
                                ) {}
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (isProcessing) "Working on your request" else "Personal AI assistant",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = onOpenBrowser) {
                        Icon(Icons.Default.Language, contentDescription = "Open browser", tint = PrimaryBlue)
                    }
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { showClearChatConfirmation = true }, enabled = !isProcessing) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Clear conversation", tint = TextSecondary)
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp)
            ) {
                // Keep the composer compact; starter prompts live in the empty-state content.
                if (messages.isEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        item {
                            SuggestionChip(
                                onClick = { textInput = "Every morning at 9 AM, check my important emails and prepare a summary." },
                                label = { Text("⏰ Schedule 9 AM email summary") }
                            )
                        }
                        item {
                            SuggestionChip(
                                onClick = { textInput = "Remember that my manager is Alex and email is alex@aurviz.com" },
                                label = { Text("🧠 Save manager info") }
                            )
                        }
                        item {
                            SuggestionChip(
                                onClick = { textInput = "Browse https://news.ycombinator.com and extract top headlines" },
                                label = { Text("🌐 Scrape tech headlines") }
                            )
                        }
                        item {
                            SuggestionChip(
                                onClick = { textInput = "Send an email to my manager with a project status update." },
                                label = { Text("✉️ Send email (HITL Gate)") }
                            )
                        }
                    }
                }

                // Input Bar with Animated Voice Button & Send
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = {
                            if (isListening) viewModel.stopVoiceInput()
                            else viewModel.startVoiceInput()
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isListening) ErrorRed else PrimaryBlue.copy(alpha = 0.12f)
                        )
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Voice Input",
                            tint = if (isListening) Color.White else PrimaryBlue
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text(if (isListening) "Listening to voice input..." else "Speak or command your agent...") },
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        shape = RoundedCornerShape(24.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                viewModel.sendPrompt(textInput)
                                textInput = ""
                            }
                        },
                        enabled = textInput.isNotBlank() && !isProcessing,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = PrimaryBlue,
                            disabledContainerColor = Color.LightGray
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (messages.isEmpty()) {
                // Empty state greeting
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = PrimaryBlue
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Aurviz Personal AI Agent",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Autonomous mobile agent that understands voice commands, manages personal memories, automates web navigation, and executes scheduled real-world tasks.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageItem(message)
                    }

                    if (isProcessing) {
                        item {
                            ProcessingIndicator()
                        }
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
                    text = { Text("This removes the saved chat history from this device.") },
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

@Composable
fun MessageItem(message: AgentMessage) {
    when (message) {
        is AgentMessage.UserPrompt -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp),
                    color = PrimaryBlue,
                    contentColor = Color.White,
                    modifier = Modifier.widthIn(max = 300.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (message.isVoiceInput) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Voice Command", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Text(message.text, fontSize = 15.sp)
                    }
                }
            }
        }
        is AgentMessage.ToolAction -> {
            var expanded by remember { mutableStateOf(false) }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ToolBadgeBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryBlue)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tool: ${message.toolName}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        StatusBadge(message.status)
                    }

                    if (!message.resultOutput.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = message.resultOutput,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            maxLines = if (expanded) Int.MAX_VALUE else 4
                        )
                        if (!expanded && message.resultOutput.length > 120) {
                            Text(
                                "Tap to view full output...",
                                fontSize = 11.sp,
                                color = PrimaryBlue,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
        is AgentMessage.AgentResponse -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp),
                    color = SurfaceLight,
                    tonalElevation = 2.dp,
                    shadowElevation = 1.dp,
                    modifier = Modifier.widthIn(max = 320.dp)
                ) {
                    Text(
                        message.text,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                }
            }
        }
        is AgentMessage.ErrorMessage -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ErrorRed.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(message.error, color = ErrorRed, fontSize = 13.sp)
                }
            }
        }
        is AgentMessage.AgentReasoning -> {
            Text(
                "💭 ${message.thought}",
                fontSize = 12.sp,
                color = TextSecondary,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}

@Composable
fun StatusBadge(status: ToolStatus) {
    val (color, text) = when (status) {
        ToolStatus.RUNNING -> Pair(WarningOrange, "Running...")
        ToolStatus.AWAITING_CONFIRMATION -> Pair(WarningOrange, "Pending Approval")
        ToolStatus.CONFIRMED -> Pair(PrimaryBlue, "Confirmed")
        ToolStatus.CANCELLED -> Pair(ErrorRed, "Cancelled")
        ToolStatus.SUCCESS -> Pair(SuccessGreen, "Success")
        ToolStatus.FAILED -> Pair(ErrorRed, "Failed")
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun ProcessingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(8.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Agent is reasoning and planning...", fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
fun ConfirmationDialog(
    action: SensitiveAction,
    onConfirm: (Map<String, Any?>?) -> Unit,
    onDismiss: () -> Unit
) {
    var editableRecipient by remember(action.actionId) { mutableStateOf(if (action is SensitiveAction.SendEmail) action.recipient else "") }
    var editableSubject by remember(action.actionId) { mutableStateOf(if (action is SensitiveAction.SendEmail) action.subject else "") }
    var editableBody by remember(action.actionId) { mutableStateOf(if (action is SensitiveAction.SendEmail) action.body else "") }
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
                    is SensitiveAction.DeleteData -> "Sensitive action approval"
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
                        is SensitiveAction.DeleteData -> "The agent is requesting permission to execute an external action. Review the details below:"
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
                    is SensitiveAction.DeleteData -> {
                        Text("Target: ${action.target}", color = ErrorRed, fontWeight = FontWeight.Bold)
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
                    val editedArguments: Map<String, Any?>? = when (action) {
                        is SensitiveAction.SendEmail -> mapOf(
                            "recipient" to editableRecipient,
                            "subject" to editableSubject,
                            "body" to editableBody
                        )
                        is SensitiveAction.BrowserInteraction -> buildMap {
                            put("selector", action.selector)
                            action.text?.let { put("text", it) }
                        }
                        is SensitiveAction.DeleteData -> null
                    }
                    onConfirm(editedArguments)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text(
                    when (action) {
                        is SensitiveAction.SendEmail -> "Send email"
                        is SensitiveAction.BrowserInteraction -> "Approve action"
                        is SensitiveAction.DeleteData -> "Approve & Execute"
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
