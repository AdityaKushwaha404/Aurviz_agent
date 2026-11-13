package com.aurviz.agent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aurviz.agent.presentation.agent.AgentViewModel
import com.aurviz.agent.presentation.memory.MemoryViewModel
import com.aurviz.agent.presentation.navigation.AppNavigation
import com.aurviz.agent.presentation.settings.SettingsViewModel
import com.aurviz.agent.presentation.tasks.TasksViewModel
import com.aurviz.agent.presentation.theme.AurvizTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var backendAuthManager: com.aurviz.agent.data.remote.BackendAuthManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()

        val app = application as AurvizApp
        backendAuthManager = app.backendAuthManager
        val agentViewModel = AgentViewModel(
            orchestrator = app.agentOrchestrator,
            voiceManager = app.voiceManager,
            securePreferences = app.securePreferences
        )
        val memoryViewModel = MemoryViewModel(memoryRepository = app.memoryRepository)
        val tasksViewModel = TasksViewModel(schedulerRepository = app.taskSchedulerRepository)
        val settingsViewModel = SettingsViewModel(
            securePreferences = app.securePreferences,
            backendAuthManager = backendAuthManager,
            performanceRecorder = app.performanceRecorder
        )

        setContent {
            AurvizTheme {
                AppNavigation(
                    agentViewModel = agentViewModel,
                    memoryViewModel = memoryViewModel,
                    tasksViewModel = tasksViewModel,
                    settingsViewModel = settingsViewModel,
                    browserAgentController = app.browserAgentController,
                    onConnectGmail = ::startGmailAuthorization
                )
            }
        }
        consumeOAuthCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeOAuthCallback(intent)
    }

    private fun startGmailAuthorization() {
        val authorizationUrl = Uri.parse(BuildConfig.API_BASE_URL.trimEnd('/') + "/auth/google")
        startActivity(Intent(Intent.ACTION_VIEW, authorizationUrl))
    }

    private fun consumeOAuthCallback(callbackIntent: Intent?) {
        val uri = callbackIntent?.data ?: return
        if (uri.scheme != "aurviz" || uri.host != "oauth" || uri.path != "/callback") return

        callbackIntent.data = null
        setIntent(callbackIntent)
        lifecycleScope.launch {
            backendAuthManager.handleOAuthCallback(uri)
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
        if (missing.isNotEmpty()) requestPermissionLauncher.launch(missing.toTypedArray())
    }
}
