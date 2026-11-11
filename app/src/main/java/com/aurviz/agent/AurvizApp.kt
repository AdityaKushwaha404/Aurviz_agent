package com.aurviz.agent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.aurviz.agent.core.security.SecurePreferences
import com.aurviz.agent.data.local.AppDatabase
import com.aurviz.agent.data.remote.GeminiAgentClient
import com.aurviz.agent.data.repository.MemoryRepositoryImpl
import com.aurviz.agent.data.repository.TaskSchedulerRepositoryImpl
import com.aurviz.agent.data.tools.EmailTool
import com.aurviz.agent.data.tools.MemoryTool
import com.aurviz.agent.data.tools.SchedulerTool
import com.aurviz.agent.data.tools.WebAutomationTool
import com.aurviz.agent.domain.orchestrator.AgentOrchestrator
import com.aurviz.agent.domain.repository.MemoryRepository
import com.aurviz.agent.domain.repository.TaskSchedulerRepository
import com.aurviz.agent.domain.tool.ToolRegistry
import com.aurviz.agent.service.voice.VoiceManager
import com.aurviz.agent.service.web.ControlledWebScraper

class AurvizApp : Application() {

    lateinit var securePreferences: SecurePreferences private set
    lateinit var database: AppDatabase private set
    lateinit var memoryRepository: MemoryRepository private set
    lateinit var taskSchedulerRepository: TaskSchedulerRepository private set
    lateinit var agentOrchestrator: AgentOrchestrator private set
    lateinit var voiceManager: VoiceManager private set

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize Security and Database
        securePreferences = SecurePreferences(this)
        database = AppDatabase.getInstance(this)
        memoryRepository = MemoryRepositoryImpl(database.memoryDao())
        taskSchedulerRepository = TaskSchedulerRepositoryImpl(this, database.scheduledTaskDao())

        // 2. Initialize Web Automation Engine
        val webScraper = ControlledWebScraper()

        // 3. Register Agent Tools
        val emailTool = EmailTool(this, securePreferences)
        val webTool = WebAutomationTool(webScraper)
        val schedulerTool = SchedulerTool(taskSchedulerRepository)
        val memoryTool = MemoryTool(memoryRepository)

        val toolRegistry = ToolRegistry(
            listOf(emailTool, webTool, schedulerTool, memoryTool)
        )

        // 4. Initialize Gemini Client and Orchestrator
        val geminiClient = GeminiAgentClient(securePreferences)
        agentOrchestrator = AgentOrchestrator(geminiClient, toolRegistry, memoryRepository)

        // 5. Initialize Voice Manager
        voiceManager = VoiceManager(this)

        // 6. Setup Notification Channels
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "aurviz_agent_tasks",
                "Aurviz Agent Background Tasks",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for recurring and scheduled agent tasks"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
