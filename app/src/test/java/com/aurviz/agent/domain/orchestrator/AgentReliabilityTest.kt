package com.aurviz.agent.domain.orchestrator

import com.aurviz.agent.data.remote.GeminiAgentResponse
import com.aurviz.agent.data.remote.GeminiErrorKind
import com.aurviz.agent.domain.metrics.PerformanceRecorder
import com.aurviz.agent.domain.model.AgentMessage
import com.aurviz.agent.domain.model.Memory
import com.aurviz.agent.domain.model.ToolExecutionRequest
import com.aurviz.agent.domain.model.ToolExecutionResponse
import com.aurviz.agent.domain.tool.AgentTool
import com.aurviz.agent.domain.tool.ToolRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Error reporting, step limits, retry classification and latency measurement. */
class AgentReliabilityTest {

    private val memories = AgentOrchestratorTest.FakeMemoryRepository()

    @Test
    fun networkFailure_showsRealErrorWithRetryInsteadOfGenericGreeting() = runBlocking {
        val client = AgentOrchestratorTest.FakeGeminiClient().apply {
            nextResponse = GeminiAgentResponse.Error("No internet connection.", GeminiErrorKind.NO_NETWORK)
        }
        val orchestrator = AgentOrchestrator(client, ToolRegistry(emptyList()), memories)

        orchestrator.processUserPrompt("Check my important emails")

        val error = orchestrator.messages.value.last() as AgentMessage.ErrorMessage
        assertEquals("No internet connection.", error.error)
        assertEquals("Check my important emails", error.retryPrompt)
        assertFalse(orchestrator.messages.value.any { it is AgentMessage.AgentResponse })
    }

    @Test
    fun nonRetryableFailure_hasNoRetryAction() = runBlocking {
        val client = AgentOrchestratorTest.FakeGeminiClient().apply {
            nextResponse = GeminiAgentResponse.Error("API key missing", GeminiErrorKind.MISSING_API_KEY, retryable = false)
        }
        val orchestrator = AgentOrchestrator(client, ToolRegistry(emptyList()), memories)

        orchestrator.processUserPrompt("Hello")

        assertNull((orchestrator.messages.value.last() as AgentMessage.ErrorMessage).retryPrompt)
    }

    @Test
    fun interactiveStepLimit_neverEndsSilently() = runBlocking {
        val tool = AgentOrchestratorTest.RecordingTool("loop")
        val client = AgentOrchestratorTest.FakeGeminiClient().apply {
            nextResponse = GeminiAgentResponse.Success("", ToolExecutionRequest("loop", emptyMap()))
        }
        val orchestrator = AgentOrchestrator(client, ToolRegistry(listOf(tool)), memories)

        orchestrator.processUserPrompt("Do something long")

        assertEquals(MAX_INTERACTIVE_STEPS, client.callCount)
        val last = orchestrator.messages.value.last()
        assertTrue(last is AgentMessage.ErrorMessage)
        assertFalse(orchestrator.isProcessing.value)
    }

    @Test
    fun multiStepEmailWorkflow_searchesReadsThenSummarizes() = runBlocking {
        val search = AgentOrchestratorTest.RecordingTool("search_emails")
        val read = AgentOrchestratorTest.RecordingTool("read_email")
        val client = AgentOrchestratorTest.FakeGeminiClient().apply {
            responses += GeminiAgentResponse.Success("", ToolExecutionRequest("search_emails", mapOf("query" to "subject:interview")))
            responses += GeminiAgentResponse.Success("", ToolExecutionRequest("read_email", mapOf("messageId" to "abc123")))
            responses += GeminiAgentResponse.Success("You have one interview invitation on Monday.")
        }
        val orchestrator = AgentOrchestrator(client, ToolRegistry(listOf(search, read)), memories)

        orchestrator.processUserPrompt("Check my emails, find interview invitations, and summarize them")

        val tools = orchestrator.messages.value.filterIsInstance<AgentMessage.ToolAction>().map { it.toolName }
        assertEquals(listOf("search_emails", "read_email"), tools)
        assertEquals(
            "You have one interview invitation on Monday.",
            (orchestrator.messages.value.last() as AgentMessage.AgentResponse).text
        )
    }

    @Test
    fun savedPreference_isInjectedIntoSystemInstruction() = runBlocking {
        memories.insertMemory(Memory(key = "email_tone", content = "I prefer formal emails", category = "preference"))
        var capturedInstruction = ""
        val client = object : com.aurviz.agent.data.remote.GeminiAgentClient(null) {
            override suspend fun generateAgentTurn(
                systemInstruction: String,
                conversationHistory: List<com.aurviz.agent.data.remote.GeminiConversationTurn>,
                toolsPrompt: String
            ): GeminiAgentResponse {
                capturedInstruction = systemInstruction
                return GeminiAgentResponse.Success("Dear Professor, ...")
            }
        }
        val orchestrator = AgentOrchestrator(client, ToolRegistry(emptyList()), memories)

        orchestrator.processUserPrompt("Draft an email to my professor")

        assertTrue(capturedInstruction.contains("I prefer formal emails"))
        assertTrue(capturedInstruction.contains("create_email_draft"))
    }

    @Test
    fun scheduledTask_transientToolFailureIsRetryableAndNotSuccess() = runBlocking {
        val flaky = object : AgentTool {
            override val name = "search_emails"
            override val description = "flaky"
            override suspend fun execute(arguments: Map<String, Any?>) =
                ToolExecutionResponse(name, false, "Gmail is temporarily unavailable.", retryable = true)
        }
        val client = AgentOrchestratorTest.FakeGeminiClient().apply {
            responses += GeminiAgentResponse.Success("", ToolExecutionRequest("search_emails", emptyMap()))
        }
        val orchestrator = AgentOrchestrator(client, ToolRegistry(listOf(flaky)), memories)

        val result = orchestrator.executeAutonomousTask("Summarize my important emails")

        assertTrue(result is AutonomousTaskResult.Failure)
        assertTrue((result as AutonomousTaskResult.Failure).retryable)
    }

    @Test
    fun latencyIsRecordedForTurnModelMemoryAndTools() = runBlocking {
        val recorder = RecordingPerformance()
        var now = 0L
        val tool = AgentOrchestratorTest.RecordingTool("lookup")
        val client = AgentOrchestratorTest.FakeGeminiClient().apply {
            responses += GeminiAgentResponse.Success("", ToolExecutionRequest("lookup", emptyMap()))
            responses += GeminiAgentResponse.Success("Done")
        }
        val orchestrator = AgentOrchestrator(
            client, ToolRegistry(listOf(tool)), memories,
            performanceRecorder = recorder,
            clock = { now.also { now += 10 } }
        )

        orchestrator.processUserPrompt("Look it up")

        val metrics = recorder.samples.map { it.first }
        assertEquals(1, metrics.count { it == PerformanceRecorder.AGENT_TURN })
        assertEquals(2, metrics.count { it == PerformanceRecorder.LLM_CALL })
        assertEquals(1, metrics.count { it == PerformanceRecorder.MEMORY_RETRIEVAL })
        assertEquals(1, metrics.count { it == PerformanceRecorder.tool("lookup") })
        assertTrue(recorder.samples.first { it.first == PerformanceRecorder.AGENT_TURN }.third)
    }

    private class RecordingPerformance : PerformanceRecorder {
        val samples = mutableListOf<Triple<String, Long, Boolean>>()
        override suspend fun record(metric: String, durationMs: Long, success: Boolean) {
            samples += Triple(metric, durationMs, success)
        }
    }
}
