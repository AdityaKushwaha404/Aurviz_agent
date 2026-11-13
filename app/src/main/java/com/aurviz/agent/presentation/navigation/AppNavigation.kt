package com.aurviz.agent.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.aurviz.agent.presentation.theme.SurfaceLight
import com.aurviz.agent.presentation.theme.TextPrimary
import com.aurviz.agent.presentation.theme.PrimaryBlue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aurviz.agent.presentation.agent.AgentScreen
import com.aurviz.agent.presentation.agent.AgentViewModel
import com.aurviz.agent.presentation.memory.MemoryScreen
import com.aurviz.agent.presentation.memory.MemoryViewModel
import com.aurviz.agent.presentation.settings.SettingsScreen
import com.aurviz.agent.presentation.settings.SettingsViewModel
import com.aurviz.agent.presentation.tasks.TasksScreen
import com.aurviz.agent.presentation.tasks.TasksViewModel
import com.aurviz.agent.presentation.browser.ControlledBrowserScreen
import com.aurviz.agent.service.web.BrowserAgentController

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Agent : Screen("agent", "Agent", Icons.Default.SmartToy)
    object Memory : Screen("memory", "Memory", Icons.Default.Psychology)
    object Tasks : Screen("tasks", "Tasks", Icons.Default.Schedule)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Browser : Screen("browser?url={url}", "Browser", Icons.Default.Language)
    object Mailbox : Screen("mailbox", "Mailbox", Icons.Default.Settings)
}

@Composable
fun AppNavigation(
    agentViewModel: AgentViewModel,
    memoryViewModel: MemoryViewModel,
    tasksViewModel: TasksViewModel,
    settingsViewModel: SettingsViewModel,
    mailboxViewModel: com.aurviz.agent.presentation.mailbox.MailboxViewModel,
    browserAgentController: BrowserAgentController,
    browserStore: com.aurviz.agent.service.web.BrowserStore,
    onConnectGmail: () -> Unit,
    shortcut: String? = null,
    onShortcutHandled: () -> Unit = {},
    sharedText: String? = null,
    onSharedTextHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    LaunchedEffect(browserAgentController, navController) {
        browserAgentController.browserScreenRequests.collect {
            if (navController.currentDestination?.route != Screen.Browser.route) {
                navController.navigate("browser?url=") { launchSingleTop = true }
            }
        }
    }
    // Opens a bottom-bar tab the same way tapping it does, so tab back stacks never nest.
    fun openTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Launcher shortcuts and text shared from other apps.
    LaunchedEffect(shortcut) {
        when (shortcut) {
            "voice" -> { openTab(Screen.Agent.route); agentViewModel.startVoiceInput() }
            "inbox" -> {
                openTab(Screen.Agent.route)
                agentViewModel.sendPrompt("Check my important emails from the last 3 days and summarize them.")
            }
            "tasks" -> openTab(Screen.Tasks.route)
            "settings" -> openTab(Screen.Settings.route)
            "browser" -> navController.navigate("browser?url=") { launchSingleTop = true }
        }
        if (shortcut != null) onShortcutHandled()
    }
    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            openTab(Screen.Agent.route)
            agentViewModel.sendPrompt("Summarize the text I shared with Aurviz.", attachment = sharedText.take(8_000))
            onSharedTextHandled()
        }
    }

    val items = listOf(
        Screen.Agent,
        Screen.Memory,
        Screen.Tasks,
        Screen.Settings
    )

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            if (currentRoute in items.map { it.route }) {
                NavigationBar(
                    containerColor = SurfaceLight,
                    contentColor = TextPrimary,
                    tonalElevation = 2.dp
                ) {
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryBlue,
                                selectedTextColor = PrimaryBlue,
                                indicatorColor = PrimaryBlue.copy(alpha = 0.12f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Agent.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(tween(240)) + slideInVertically(tween(280)) { it / 28 } },
            exitTransition = { fadeOut(tween(140)) },
            popEnterTransition = { fadeIn(tween(240)) },
            popExitTransition = { fadeOut(tween(140)) + slideOutVertically(tween(220)) { it / 28 } }
        ) {
            composable(Screen.Agent.route) {
                AgentScreen(
                    viewModel = agentViewModel,
                    onOpenBrowser = { navController.navigate("browser?url=") },
                    onOpenMailbox = { navController.navigate(Screen.Mailbox.route) { launchSingleTop = true } },
                    onOpenSettings = { openTab(Screen.Settings.route) }
                )
            }
            composable(Screen.Memory.route) {
                MemoryScreen(viewModel = memoryViewModel)
            }
            composable(Screen.Tasks.route) {
                TasksScreen(viewModel = tasksViewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onConnectGmail = onConnectGmail,
                    onDisconnectGmail = settingsViewModel::disconnectGmail,
                    onOpenMailbox = { navController.navigate(Screen.Mailbox.route) { launchSingleTop = true } }
                )
            }
            composable(Screen.Mailbox.route) {
                com.aurviz.agent.presentation.mailbox.MailboxScreen(
                    viewModel = mailboxViewModel,
                    onBack = { navController.popBackStack() },
                    onAskAgent = { prompt, attachment ->
                        navController.popBackStack(Screen.Agent.route, inclusive = false)
                        agentViewModel.sendPrompt(prompt, attachment = attachment)
                    }
                )
            }
            composable(Screen.Browser.route) { entry ->
                val initialUrl = entry.arguments?.getString("url").orEmpty()
                ControlledBrowserScreen(
                    initialUrl = initialUrl,
                    controller = browserAgentController,
                    store = browserStore,
                    onBack = { navController.popBackStack() },
                    onAskAgent = { prompt, attachment ->
                        navController.popBackStack()
                        agentViewModel.sendPrompt(prompt, attachment = attachment)
                    }
                )
            }
        }
    }
}
