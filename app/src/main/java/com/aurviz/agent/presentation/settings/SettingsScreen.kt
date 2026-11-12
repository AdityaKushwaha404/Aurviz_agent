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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onConnectGmail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gmailConnectionState by viewModel.gmailConnectionState.collectAsState()
    val currentApiKey by viewModel.apiKey.collectAsState()
    val currentEmail by viewModel.defaultEmail.collectAsState()
    val voiceFeedback by viewModel.voiceFeedback.collectAsState()
    val storageMessage by viewModel.storageMessage.collectAsState()

    var keyInput by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var emailInput by remember(currentEmail) { mutableStateOf(currentEmail) }
    var keyVisible by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

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

            // Gmail delivery
            Text("Email delivery", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                        "Aurviz sends only after you approve the recipient, subject, and message. Google grants send-only Gmail access.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Button(onClick = onConnectGmail) {
                        Text("Connect Gmail")
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
        }
    }
}
