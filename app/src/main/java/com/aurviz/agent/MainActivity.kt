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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.aurviz.agent.presentation.components.SplashOverlay
import com.aurviz.agent.presentation.onboarding.OnboardingScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var backendAuthManager: com.aurviz.agent.data.remote.BackendAuthManager

    private var pendingShortcut by mutableStateOf<String?>(null)
    private var pendingSharedText by mutableStateOf<String?>(null)

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
            val themeMode by settingsViewModel.themeMode.collectAsState()
            AurvizTheme(
                darkTheme = when (themeMode) {
                    "LIGHT" -> false
                    "DARK" -> true
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                }
            ) {
                var showSplash by rememberSaveable { mutableStateOf(true) }
                var showOnboarding by rememberSaveable { mutableStateOf(!app.securePreferences.isOnboardingDone()) }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (showOnboarding) {
                        OnboardingScreen(onFinish = { useDemo ->
                            if (useDemo) app.securePreferences.setDemoMode(true)
                            app.securePreferences.setOnboardingDone()
                            showOnboarding = false
                        })
                    } else {
                        AppNavigation(
                            agentViewModel = agentViewModel,
                            memoryViewModel = memoryViewModel,
                            tasksViewModel = tasksViewModel,
                            settingsViewModel = settingsViewModel,
                            browserAgentController = app.browserAgentController,
                            browserStore = app.browserStore,
                            onConnectGmail = ::startGmailAuthorization,
                            shortcut = pendingShortcut,
                            onShortcutHandled = { pendingShortcut = null },
                            sharedText = pendingSharedText,
                            onSharedTextHandled = { pendingSharedText = null }
                        )
                    }
                    AnimatedVisibility(
                        visible = showSplash,
                        enter = fadeIn(),
                        exit = fadeOut(tween(350))
                    ) {
                        SplashOverlay(onFinished = { showSplash = false })
                    }
                }
            }
        }
        handleLaunchIntent(intent)
        consumeOAuthCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeOAuthCallback(intent)
        handleLaunchIntent(intent)
    }

    /** Launcher shortcuts (aurviz://shortcut/...) and text shared from other apps. */
    private fun handleLaunchIntent(launchIntent: Intent?) {
        launchIntent ?: return
        val data = launchIntent.data
        if (data != null && data.scheme == "aurviz" && data.host == "shortcut") {
            pendingShortcut = data.pathSegments.firstOrNull()
            launchIntent.data = null
        } else if (launchIntent.action == Intent.ACTION_SEND && launchIntent.type?.startsWith("text/") == true) {
            pendingSharedText = launchIntent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
            launchIntent.action = null
        }
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
