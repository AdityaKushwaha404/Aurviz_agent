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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsState()
    val isListening by viewModel.isListening.collectAsState()

    var textInput by remember { mutableStateOf("") }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (isProcessing) WarningOrange else SuccessGreen,
                            modifier = Modifier.size(10.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Aurviz Autonomous Agent", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(
                                if (isProcessing) "Reasoning & Executing Tools..." else "Ready • Free Gemini 1.5 Flash",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp)
            ) {
                // Quick Suggestion Chips
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
                            imageVector = Icons.Default.Send,
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
                    horizontalAlignment = Alignment.CenterVertically,
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
                    onConfirm = { viewModel.onConfirmAction(true) },
                    onDismiss = { viewModel.onConfirmAction(false) }
                )
            }
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
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var editableRecipient by remember { mutableStateOf(if (action is SensitiveAction.SendEmail) action.recipient else "") }
    var editableSubject by remember { mutableStateOf(if (action is SensitiveAction.SendEmail) action.subject else "") }
    var editableBody by remember { mutableStateOf(if (action is SensitiveAction.SendEmail) action.body else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Security, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(32.dp))
        },
        title = {
            Text("Sensitive Action Approval Required", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The agent is requesting permission to execute an external action. You can review or edit the details below:",
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
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Approve & Execute")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
