package com.aurviz.agent.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
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

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Agent : Screen("agent", "Agent", Icons.Default.SmartToy)
    object Memory : Screen("memory", "Memory", Icons.Default.Psychology)
    object Tasks : Screen("tasks", "Tasks", Icons.Default.Schedule)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun AppNavigation(
    agentViewModel: AgentViewModel,
    memoryViewModel: MemoryViewModel,
    tasksViewModel: TasksViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()
    val items = listOf(
        Screen.Agent,
        Screen.Memory,
        Screen.Tasks,
        Screen.Settings
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Agent.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Agent.route) {
                AgentScreen(viewModel = agentViewModel)
            }
            composable(Screen.Memory.route) {
                MemoryScreen(viewModel = memoryViewModel)
            }
            composable(Screen.Tasks.route) {
                TasksScreen(viewModel = tasksViewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = settingsViewModel)
            }
        }
    }
}
