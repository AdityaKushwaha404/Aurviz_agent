package com.aurviz.agent.presentation.settings

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
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
    modifier: Modifier = Modifier
) {
    val currentApiKey by viewModel.apiKey.collectAsState()
    val currentEmail by viewModel.defaultEmail.collectAsState()
    val voiceFeedback by viewModel.voiceFeedback.collectAsState()

    var keyInput by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var emailInput by remember(currentEmail) { mutableStateOf(currentEmail) }
    var keyVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current
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
                        Text("Hardware-Backed Keystore Security", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Your API keys and credentials are encrypted using AES-256 GCM in Android EncryptedSharedPreferences and are excluded from cloud backups.",
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
                    Toast.makeText(context, "Gemini API key saved securely!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Save API Key")
            }

            HorizontalDivider()

            // Tool Configuration Section
            Text("Tool Configuration", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            OutlinedTextField(
                value = emailInput,
                onValueChange = { emailInput = it },
                label = { Text("Default Email Account") },
                placeholder = { Text("user@company.com") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = {
                    viewModel.updateDefaultEmail(emailInput)
                    Toast.makeText(context, "Default email saved!", Toast.LENGTH_SHORT).show()
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
