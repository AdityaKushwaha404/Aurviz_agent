package com.aurviz.agent.domain.orchestrator

import com.aurviz.agent.data.remote.GeminiAgentClient
import com.aurviz.agent.data.remote.GeminiAgentResponse
import com.aurviz.agent.domain.model.AgentMessage
import com.aurviz.agent.domain.model.Memory
import com.aurviz.agent.domain.model.SensitiveAction
import com.aurviz.agent.domain.model.ToolExecutionRequest
import com.aurviz.agent.domain.model.ToolExecutionResponse
import com.aurviz.agent.domain.repository.MemoryRepository
import com.aurviz.agent.domain.tool.AgentTool
import com.aurviz.agent.domain.tool.ToolRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AgentOrchestratorTest {

    private lateinit var fakeMemoryRepo: FakeMemoryRepository
    private lateinit var fakeGeminiClient: FakeGeminiClient
    private lateinit var orchestrator: AgentOrchestrator

    @Before
    fun setup() {
        fakeMemoryRepo = FakeMemoryRepository()
        fakeGeminiClient = FakeGeminiClient()

        val fakeEmailTool = object : AgentTool {
            override val name: String = "send_email"
            override val description: String = "Send an email"
            override val isSensitive: Boolean = true

            override suspend fun execute(arguments: Map<String, Any?>): ToolExecutionResponse {
                return ToolExecutionResponse(name, false, "Confirmation required", SensitiveAction.SendEmail(
                    description = "Send email",
                    recipient = "test@example.com",
                    subject = "Test",
                    body = "Body"
                ))
            }

            override suspend fun executeConfirmed(actionId: String, payload: Map<String, Any?>): ToolExecutionResponse {
                return ToolExecutionResponse(name, true, "Email sent successfully")
            }
        }

        val toolRegistry = ToolRegistry(listOf(fakeEmailTool))
        orchestrator = AgentOrchestrator(fakeGeminiClient as GeminiAgentClient, toolRegistry, fakeMemoryRepo)
    }

    @Test
    fun processUserPrompt_generatesDirectResponse() = runBlocking {
        fakeGeminiClient.nextResponse = GeminiAgentResponse.Success("Hello! I am ready to help.")

        orchestrator.processUserPrompt("Hello")

        val messages = orchestrator.messages.value
        assertTrue(messages.any { it is AgentMessage.UserPrompt })
        assertTrue(messages.any { it is AgentMessage.AgentResponse && it.text == "Hello! I am ready to help." })
    }

    @Test
    fun processUserPrompt_sensitiveTool_pausesForHITLConfirmation() = runBlocking {
        fakeGeminiClient.nextResponse = GeminiAgentResponse.Success(
            text = "I will draft the email for you.",
            toolCall = ToolExecutionRequest("send_email", mapOf("recipient" to "boss@aurviz.com", "subject" to "Report", "body" to "Hello"))
        )

        // Launch prompt processing
        val job = kotlinx.coroutines.GlobalScope.let {
            kotlinx.coroutines.launch(kotlinx.coroutines.Dispatchers.Default) {
                orchestrator.processUserPrompt("Send an email to my boss")
            }
        }

        // Wait brief moment for pending confirmation state
        kotlinx.coroutines.delay(100)

        // Verify that sensitive action is awaiting confirmation
        val pending = orchestrator.pendingConfirmation.value
        assertNotNull(pending)
        assertTrue(pending is SensitiveAction.SendEmail)

        // Confirm the action
        orchestrator.confirmPendingAction(approved = true)
        job.join()

        // Verify confirmation cleared
        assertNull(orchestrator.pendingConfirmation.value)
    }

    class FakeMemoryRepository : MemoryRepository {
        private val list = mutableListOf<Memory>()
        override fun getAllMemories(): Flow<List<Memory>> = flowOf(list)
        override suspend fun insertMemory(memory: Memory): Long { list.add(memory); return 1L }
        override suspend fun deleteMemory(id: Long) { list.removeIf { it.id == id } }
        override suspend fun searchMemories(query: String): List<Memory> = list
        override suspend fun getRecentMemories(limit: Int): List<Memory> = list.take(limit)
    }

    class FakeGeminiClient : GeminiAgentClient(object : com.aurviz.agent.core.security.SecurePreferences(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()) {
        override fun getGeminiApiKey(): String = "test_key"
    }) {
        var nextResponse: GeminiAgentResponse = GeminiAgentResponse.Success("OK")

        override suspend fun generateAgentTurn(
            systemInstruction: String,
            conversationHistory: List<Pair<String, String>>,
            toolsPrompt: String
        ): GeminiAgentResponse = nextResponse
    }
}
