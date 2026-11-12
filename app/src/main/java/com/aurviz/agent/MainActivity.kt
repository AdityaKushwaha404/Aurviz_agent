package com.aurviz.agent

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.aurviz.agent.presentation.agent.AgentViewModel
import com.aurviz.agent.presentation.memory.MemoryViewModel
import com.aurviz.agent.presentation.navigation.AppNavigation
import com.aurviz.agent.presentation.settings.SettingsViewModel
import com.aurviz.agent.presentation.tasks.TasksViewModel
import com.aurviz.agent.presentation.theme.AurvizTheme

class MainActivity : ComponentActivity() {

    private lateinit var gmailAccessManager: com.aurviz.agent.data.remote.GmailAccessManager

    private val gmailAuthorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        gmailAccessManager.handleAuthorizationResult(this, result.data)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request runtime permissions (Microphone & Notifications)
        checkAndRequestPermissions()

        val app = application as AurvizApp
        gmailAccessManager = app.gmailAccessManager

        val agentViewModel = AgentViewModel(
            orchestrator = app.agentOrchestrator,
            voiceManager = app.voiceManager,
            securePreferences = app.securePreferences
        )

        val memoryViewModel = MemoryViewModel(
            memoryRepository = app.memoryRepository
        )

        val tasksViewModel = TasksViewModel(
            schedulerRepository = app.taskSchedulerRepository
        )

        val settingsViewModel = SettingsViewModel(
            securePreferences = app.securePreferences,
            gmailAccessManager = app.gmailAccessManager
        )

        setContent {
            AurvizTheme {
                AppNavigation(
                    agentViewModel = agentViewModel,
                    memoryViewModel = memoryViewModel,
                    tasksViewModel = tasksViewModel,
                    settingsViewModel = settingsViewModel,
                    browserAgentController = app.browserAgentController,
                    onConnectGmail = {
                        gmailAccessManager.authorize(this) { intentSender ->
                            gmailAuthorizationLauncher.launch(
                                IntentSenderRequest.Builder(intentSender).build()
                            )
                        }
                    }
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        }
    }
}
