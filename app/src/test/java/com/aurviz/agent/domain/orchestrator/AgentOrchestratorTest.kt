package com.aurviz.agent.domain.orchestrator

import com.aurviz.agent.data.remote.GeminiAgentClient
import com.aurviz.agent.data.remote.GeminiConversationTurn
import com.aurviz.agent.data.remote.GeminiAgentResponse
import com.aurviz.agent.domain.model.AgentMessage
import com.aurviz.agent.domain.model.Memory
import com.aurviz.agent.domain.model.SensitiveAction
import com.aurviz.agent.domain.model.ToolExecutionRequest
import com.aurviz.agent.domain.model.ToolExecutionResponse
import com.aurviz.agent.domain.repository.MemoryRepository
import com.aurviz.agent.domain.repository.ConversationStore
import com.aurviz.agent.domain.tool.AgentTool
import com.aurviz.agent.domain.tool.ToolRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AgentOrchestratorTest {

    private lateinit var fakeMemoryRepo: FakeMemoryRepository
    private lateinit var fakeGeminiClient: FakeGeminiClient
    private lateinit var orchestrator: AgentOrchestrator
    private var confirmedEmailPayload: Map<String, Any?> = emptyMap()
    private var confirmedEmailExecutions: Int = 0

    @Before
    fun setup() {
        fakeMemoryRepo = FakeMemoryRepository()
        fakeGeminiClient = FakeGeminiClient()
        confirmedEmailPayload = emptyMap()
        confirmedEmailExecutions = 0

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
                confirmedEmailExecutions++
                confirmedEmailPayload = payload
                return ToolExecutionResponse(name, true, "Email sent for test")
            }
        }

        val toolRegistry = ToolRegistry(listOf(fakeEmailTool))
        orchestrator = AgentOrchestrator(fakeGeminiClient, toolRegistry, fakeMemoryRepo)
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
    fun conversationHistory_survivesRecreationAndCanBeCleared() = runBlocking {
        val history = FakeConversationStore()
        fakeGeminiClient.nextResponse = GeminiAgentResponse.Success("Persisted answer")
        val first = AgentOrchestrator(fakeGeminiClient, ToolRegistry(emptyList()), fakeMemoryRepo, history)
        first.processUserPrompt("Remember this conversation")

        val restored = AgentOrchestrator(fakeGeminiClient, ToolRegistry(emptyList()), fakeMemoryRepo, history)
        assertTrue(restored.messages.value.any { it is AgentMessage.UserPrompt && it.text == "Remember this conversation" })
        assertTrue(restored.messages.value.any { it is AgentMessage.AgentResponse && it.text == "Persisted answer" })

        restored.clearConversation()
        assertTrue(restored.messages.value.isEmpty())
        assertTrue(history.messages.isEmpty())
    }

    @Test
    fun processUserPrompt_preservesGeminiFunctionCallPartsAndResponseId() = runBlocking {
        val tool = RecordingTool("lookup")
        val modelParts = """[{"functionCall":{"name":"lookup","args":{},"id":"call-123"},"thoughtSignature":"opaque-signature"}]"""
        val client = FakeGeminiClient().apply {
            responses += GeminiAgentResponse.Success(
                text = "",
                toolCall = ToolExecutionRequest("lookup", emptyMap(), id = "call-123"),
                modelTurnPartsJson = modelParts
            )
            responses += GeminiAgentResponse.Success("Finished")
        }
        val runner = AgentOrchestrator(client, ToolRegistry(listOf(tool)), fakeMemoryRepo)

        runner.processUserPrompt("Look it up")

        val followUpHistory = client.histories[1]
        assertEquals(modelParts, followUpHistory[1].rawPartsJson)
        assertEquals("call-123", followUpHistory[2].functionResponse?.id)
        assertEquals("lookup", followUpHistory[2].functionResponse?.name)
        assertEquals(1, tool.executions)
        assertTrue(runner.messages.value.any { it is AgentMessage.AgentResponse && it.text == "Finished" })
    }

    @Test
    fun scheduledTask_runsMultipleSafeStepsAndReturnsFinalSummary() = runBlocking {
        val firstTool = RecordingTool("first")
        val secondTool = RecordingTool("second")
        val client = FakeGeminiClient().apply {
            responses += GeminiAgentResponse.Success("", ToolExecutionRequest("first", emptyMap()))
            responses += GeminiAgentResponse.Success("", ToolExecutionRequest("second", emptyMap()))
            responses += GeminiAgentResponse.Success("Finished the workflow")
        }
        val runner = AgentOrchestrator(client, ToolRegistry(listOf(firstTool, secondTool)), fakeMemoryRepo)

        val result = runner.executeAutonomousTask("Run the workflow")

        assertTrue(result.startsWith("Success:"))
        assertEquals(1, firstTool.executions)
        assertEquals(1, secondTool.executions)
        assertEquals(3, client.callCount)
        assertTrue(client.histories[1].any { it.functionCall?.toolName == "first" })
        assertTrue(client.histories[1].any { it.functionResponse?.name == "first" })
    }

    @Test
    fun scheduledTask_neverExecutesSensitiveAction() = runBlocking {
        val emailTool = RecordingTool("send_email", sensitive = true)
        fakeGeminiClient.nextResponse = GeminiAgentResponse.Success("", ToolExecutionRequest("send_email", emptyMap()))
        val runner = AgentOrchestrator(fakeGeminiClient, ToolRegistry(listOf(emailTool)), fakeMemoryRepo)

        val result = runner.executeAutonomousTask("Send an email")

        assertTrue(result.startsWith("Task failed:"))
        assertEquals(0, emailTool.executions)
    }

    @Test
    fun processUserPrompt_sensitiveTool_pausesForHITLConfirmation() = runBlocking {
        fakeGeminiClient.nextResponse = GeminiAgentResponse.Success(
            text = "I will draft the email for you.",
            toolCall = ToolExecutionRequest("send_email", mapOf("recipient" to "boss@aurviz.com", "subject" to "Report", "body" to "Hello"))
        )

        // Launch prompt processing on background dispatcher
        val job = launch(kotlinx.coroutines.Dispatchers.Default) {
            orchestrator.processUserPrompt("Send an email to my boss")
        }

        // Wait for pending confirmation state
        var pending = orchestrator.pendingConfirmation.value
        val startTime = System.currentTimeMillis()
        while (pending == null && System.currentTimeMillis() - startTime < 3000) {
            delay(50)
            pending = orchestrator.pendingConfirmation.value
        }

        assertNotNull("Pending confirmation should not be null. Messages: ${orchestrator.messages.value}", pending)
        assertTrue(pending is SensitiveAction.SendEmail)

        // Confirm with edited content and verify those exact values are executed.
        fakeGeminiClient.nextResponse = GeminiAgentResponse.Success("The test email was sent.")
        val approvedValues = mapOf(
            "recipient" to "reviewed@example.com",
            "subject" to "Reviewed subject",
            "body" to "Reviewed body"
        )
        orchestrator.confirmPendingAction(approved = true, editedArguments = approvedValues)
        job.join()

        assertNull(orchestrator.pendingConfirmation.value)
        assertEquals(1, confirmedEmailExecutions)
        assertEquals(approvedValues, confirmedEmailPayload)
    }

    class FakeMemoryRepository : MemoryRepository {
        private val list = mutableListOf<Memory>()
        override fun getAllMemories(): Flow<List<Memory>> = flowOf(list)
        override suspend fun insertMemory(memory: Memory): Long { list.add(memory); return 1L }
        override suspend fun deleteMemory(id: Long) { list.removeIf { it.id == id } }
        override suspend fun searchMemories(query: String): List<Memory> = list
        override suspend fun getRecentMemories(limit: Int): List<Memory> = list.take(limit)
    }

    class FakeConversationStore : ConversationStore {
        var messages: List<AgentMessage> = emptyList()
        override fun loadMessages(): List<AgentMessage> = messages
        override fun saveMessages(messages: List<AgentMessage>) { this.messages = messages.toList() }
        override fun clear() { messages = emptyList() }
    }

    class FakeGeminiClient : GeminiAgentClient(null) {
        var nextResponse: GeminiAgentResponse = GeminiAgentResponse.Success("OK")
        val responses = mutableListOf<GeminiAgentResponse>()
        val histories = mutableListOf<List<GeminiConversationTurn>>()
        var callCount = 0

        override suspend fun generateAgentTurn(
            systemInstruction: String,
            conversationHistory: List<GeminiConversationTurn>,
            toolsPrompt: String
        ): GeminiAgentResponse {
            callCount++
            histories += conversationHistory
            return if (responses.isNotEmpty()) responses.removeAt(0) else nextResponse
        }
    }

    class RecordingTool(
        override val name: String,
        private val sensitive: Boolean = false
    ) : AgentTool {
        var executions = 0
        override val description: String = "Test tool"
        override val isSensitive: Boolean get() = sensitive
        override suspend fun execute(arguments: Map<String, Any?>): ToolExecutionResponse {
            executions++
            return ToolExecutionResponse(name, true, "$name complete")
        }
    }
}
