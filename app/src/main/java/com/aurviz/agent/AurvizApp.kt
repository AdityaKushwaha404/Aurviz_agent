package com.aurviz.agent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.aurviz.agent.core.security.SecurePreferences
import com.aurviz.agent.data.local.AppDatabase
import com.aurviz.agent.data.local.JsonConversationStore
import com.aurviz.agent.data.remote.GeminiAgentClient
import com.aurviz.agent.data.remote.BackendApiClient
import com.aurviz.agent.data.remote.BackendAuthManager
import com.aurviz.agent.data.repository.MemoryRepositoryImpl
import com.aurviz.agent.data.repository.TaskSchedulerRepositoryImpl
import com.aurviz.agent.data.tools.EmailTool
import com.aurviz.agent.data.mail.BackendMailService
import com.aurviz.agent.data.mail.DemoMailService
import com.aurviz.agent.data.mail.MailSourceManager
import com.aurviz.agent.data.mail.PrefsDemoMailStore
import com.aurviz.agent.domain.mail.MailGateway
import com.aurviz.agent.data.tools.GmailDraftTool
import com.aurviz.agent.data.tools.GmailReadTool
import com.aurviz.agent.data.tools.GmailSearchTool
import com.aurviz.agent.data.local.metrics.RoomPerformanceRecorder
import com.aurviz.agent.data.tools.BrowserAgentTool
import com.aurviz.agent.data.tools.MemoryTool
import com.aurviz.agent.data.tools.SchedulerTool
import com.aurviz.agent.data.tools.WebAutomationTool
import com.aurviz.agent.domain.orchestrator.AgentOrchestrator
import com.aurviz.agent.domain.repository.MemoryRepository
import com.aurviz.agent.domain.repository.TaskSchedulerRepository
import com.aurviz.agent.domain.tool.ToolRegistry
import kotlinx.coroutines.launch
import com.aurviz.agent.service.voice.VoiceManager
import com.aurviz.agent.service.web.ControlledWebScraper
import com.aurviz.agent.service.web.BrowserAgentController
import com.aurviz.agent.service.web.BrowserStore

class AurvizApp : Application() {

    lateinit var securePreferences: SecurePreferences private set
    lateinit var backendAuthManager: BackendAuthManager private set
    lateinit var mailService: MailGateway private set
    lateinit var mailSourceManager: MailSourceManager private set
    lateinit var database: AppDatabase private set
    lateinit var memoryRepository: MemoryRepository private set
    lateinit var taskSchedulerRepository: TaskSchedulerRepository private set
    lateinit var agentOrchestrator: AgentOrchestrator private set
    lateinit var voiceManager: VoiceManager private set
    lateinit var browserAgentController: BrowserAgentController private set
    lateinit var performanceRecorder: RoomPerformanceRecorder private set
    lateinit var browserStore: BrowserStore private set

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize security, Gmail authorization, and database
        securePreferences = SecurePreferences(this)
        val backendApi = BackendApiClient(BuildConfig.API_BASE_URL)
        backendAuthManager = BackendAuthManager(backendApi, securePreferences)
        mailSourceManager = MailSourceManager(securePreferences, backendAuthManager)
        // Wake the hosted backend early so the first Gmail action is not slow.
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            backendApi.warmUp()
            backendAuthManager.refreshConnectionState()
        }
        database = AppDatabase.getInstance(this)
        memoryRepository = MemoryRepositoryImpl(database.memoryDao())
        taskSchedulerRepository = TaskSchedulerRepositoryImpl(
            this,
            database.scheduledTaskDao(),
            database.taskExecutionDao()
        )
        performanceRecorder = RoomPerformanceRecorder(database.performanceDao())

        // 2. Initialize browser automation sessions
        val webScraper = ControlledWebScraper()
        browserAgentController = BrowserAgentController()
        browserStore = BrowserStore(this)

        // 3. Register Agent Tools
        mailService = MailGateway(
            real = BackendMailService(backendAuthManager),
            demo = DemoMailService(store = PrefsDemoMailStore(this)),
            demoEnabled = securePreferences::isDemoMode
        )
        val emailTool = EmailTool(mailService) { securePreferences.getDefaultEmail() }
        val gmailTools = listOf(
            GmailSearchTool(mailService),
            GmailReadTool(mailService),
            GmailDraftTool(mailService) { securePreferences.getDefaultEmail() }
        )
        val webTool = WebAutomationTool(webScraper)
        val browserTools = listOf(
            BrowserAgentTool(browserAgentController, "open_url", "Open a public HTTPS URL in the controlled in-app browser."),
            BrowserAgentTool(browserAgentController, "search_webpage", "Search the current page using its search box, or search the web if no page is open."),
            BrowserAgentTool(browserAgentController, "read_page", "Read visible text from the current browser page."),
            BrowserAgentTool(browserAgentController, "click_element", "Click a page element by CSS selector after explicit user approval."),
            BrowserAgentTool(browserAgentController, "enter_text", "Enter text into a page field after explicit user approval. Does not submit the form."),
            BrowserAgentTool(browserAgentController, "scroll_page", "Scroll the current page up or down."),
            BrowserAgentTool(browserAgentController, "navigate_back", "Navigate to the previous page in browser history."),
            BrowserAgentTool(browserAgentController, "navigate_forward", "Navigate to the next page in browser history."),
            BrowserAgentTool(browserAgentController, "extract_links", "List the links (text and URL) on the current page so you can choose where to go next."),
            BrowserAgentTool(browserAgentController, "get_browser_state", "Return the current page title, URL, loading state, and a short text preview.")
        )
        val schedulerTool = SchedulerTool(taskSchedulerRepository)
        val memoryTool = MemoryTool(memoryRepository)

        val toolRegistry = ToolRegistry(
            gmailTools + listOf(emailTool, webTool, schedulerTool, memoryTool) + browserTools
        )

        // 4. Initialize Gemini Client and Orchestrator
        val geminiClient = GeminiAgentClient(securePreferences)
        agentOrchestrator = AgentOrchestrator(
            geminiClient,
            toolRegistry,
            memoryRepository,
            JsonConversationStore(java.io.File(filesDir, "aurviz-chat-history.json")),
            performanceRecorder
        )

        // 5. Initialize Voice Manager
        voiceManager = VoiceManager(this)

        // 5b. Make sure every enabled recurring task has its next run queued (also migrates old periodic jobs).
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { taskSchedulerRepository.ensureScheduled() }
        }

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
