package com.aurviz.agent.presentation.navigation

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
}

@Composable
fun AppNavigation(
    agentViewModel: AgentViewModel,
    memoryViewModel: MemoryViewModel,
    tasksViewModel: TasksViewModel,
    settingsViewModel: SettingsViewModel,
    browserAgentController: BrowserAgentController,
    onConnectGmail: () -> Unit
) {
    val navController = rememberNavController()
    LaunchedEffect(browserAgentController, navController) {
        browserAgentController.browserScreenRequests.collect {
            if (navController.currentDestination?.route != Screen.Browser.route) {
                navController.navigate("browser?url=") { launchSingleTop = true }
            }
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
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Agent.route) {
                AgentScreen(viewModel = agentViewModel, onOpenBrowser = {
                    navController.navigate("browser?url=")
                })
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
                    onDisconnectGmail = settingsViewModel::disconnectGmail
                )
            }
            composable(Screen.Browser.route) { entry ->
                val initialUrl = entry.arguments?.getString("url").orEmpty()
                ControlledBrowserScreen(
                    initialUrl = initialUrl,
                    controller = browserAgentController,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
