package com.aurviz.agent.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException
import java.util.concurrent.CopyOnWriteArrayList

class GeminiAgentClientTest {

    private val apiKey = "test-api-key-do-not-log"

    private class FakeTransport(
        private val handler: (url: String, apiKey: String, body: String) -> GeminiHttpResponse
    ) : GeminiHttpTransport {
        val requests = CopyOnWriteArrayList<Triple<String, String, String>>()

        override fun post(url: String, apiKey: String, jsonBody: String): GeminiHttpResponse {
            requests += Triple(url, apiKey, jsonBody)
            return handler(url, apiKey, jsonBody)
        }
    }

    private fun client(
        transport: GeminiHttpTransport,
        models: List<String> = listOf("gemini-3.5-flash-lite")
    ) = GeminiAgentClient(
        securePreferences = null,
        transport = transport,
        modelCandidates = models,
        apiKeyProvider = { apiKey },
        fallbackDelayMillis = 0L
    )

    private fun successBody(text: String): String =
        """{"candidates":[{"content":{"parts":[{"text":"$text"}]},"finishReason":"STOP"}]}"""

    private fun errorBody(code: Int, status: String, message: String): String =
        """{"error":{"code":$code,"message":"$message","status":"$status"}}"""

    @Test
    fun successfulResponse_usesConfiguredModelEndpointAndApiKeyHeader() = runBlocking {
        val transport = FakeTransport { url, key, body ->
            assertEquals(apiKey, key)
            assertTrue(url.contains("/v1beta/models/gemini-3.5-flash-lite:generateContent"))
            assertTrue(body.contains("\"contents\""))
            assertTrue(body.contains("system-instruction"))
            assertTrue(body.contains("tool-prompt"))
            GeminiHttpResponse(200, successBody("All done"))
        }

        val response = client(transport).generateAgentTurn("system-instruction", emptyList(), "tool-prompt")

        assertTrue(response is GeminiAgentResponse.Success)
        assertEquals("All done", (response as GeminiAgentResponse.Success).text)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun deprecatedModelFallback_retriesNextSupportedModel() = runBlocking {
        val transport = FakeTransport { url, _, _ ->
            if (url.contains("gemini-2.0-flash-lite")) {
                GeminiHttpResponse(
                    404,
                    errorBody(
                        404,
                        "NOT_FOUND",
                        "This model models/gemini-2.0-flash-lite is no longer available. " +
                            "Please update your code to use models/gemini-3.5-flash-lite."
                    )
                )
            } else {
                GeminiHttpResponse(200, successBody("Fallback succeeded"))
            }
        }

        val response = client(
            transport,
            models = listOf("gemini-2.0-flash-lite", "gemini-3.5-flash-lite")
        ).generateAgentTurn("system", emptyList(), "tools")

        assertTrue(response is GeminiAgentResponse.Success)
        assertEquals("Fallback succeeded", (response as GeminiAgentResponse.Success).text)
        assertEquals(2, transport.requests.size)
        assertTrue(transport.requests[0].first.contains("gemini-2.0-flash-lite"))
        assertTrue(transport.requests[1].first.contains("gemini-3.5-flash-lite"))
    }

    @Test
    fun modelNotFoundForEveryCandidate_returnsActionableModelError() = runBlocking {
        val transport = FakeTransport { _, _, _ ->
            GeminiHttpResponse(404, errorBody(404, "NOT_FOUND", "models/retired is not found"))
        }

        val response = client(
            transport,
            models = listOf("gemini-retired-a", "gemini-retired-b")
        ).generateAgentTurn("system", emptyList(), "tools")

        assertTrue(response is GeminiAgentResponse.Error)
        val error = response as GeminiAgentResponse.Error
        assertEquals(GeminiErrorKind.MODEL_NOT_FOUND, error.kind)
        assertEquals(404, error.statusCode)
        assertFalse(error.retryable)
        assertTrue(error.message.contains("GEMINI_MODEL"))
        assertFalse(error.message.contains(apiKey))
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun rateLimitResponse_isClassifiedAndRetryable() = runBlocking {
        val transport = FakeTransport { _, _, _ ->
            GeminiHttpResponse(429, errorBody(429, "RESOURCE_EXHAUSTED", "Quota exceeded"))
        }

        val response = client(transport).generateAgentTurn("system", emptyList(), "tools")

        assertTrue(response is GeminiAgentResponse.Error)
        val error = response as GeminiAgentResponse.Error
        assertEquals(GeminiErrorKind.RATE_LIMITED, error.kind)
        assertTrue(error.retryable)
        assertTrue(error.isQuotaExceeded)
        assertTrue(error.message.contains("429"))
    }

    @Test
    fun networkFailure_isClassifiedAndRetryableWithoutModelFallback() = runBlocking {
        val transport = FakeTransport { _, _, _ -> throw UnknownHostException("offline") }

        val response = client(
            transport,
            models = listOf("gemini-3.5-flash-lite", "gemini-3.6-flash")
        ).generateAgentTurn("system", emptyList(), "tools")

        assertTrue(response is GeminiAgentResponse.Error)
        val error = response as GeminiAgentResponse.Error
        assertEquals(GeminiErrorKind.NO_NETWORK, error.kind)
        assertTrue(error.retryable)
        assertTrue(error.message.contains("internet", ignoreCase = true))
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun unauthorizedResponse_isNotRetried() = runBlocking {
        val transport = FakeTransport { _, _, _ ->
            GeminiHttpResponse(401, errorBody(401, "UNAUTHENTICATED", "API key not valid"))
        }

        val response = client(
            transport,
            models = listOf("gemini-3.5-flash-lite", "gemini-3.6-flash")
        ).generateAgentTurn("system", emptyList(), "tools")

        assertTrue(response is GeminiAgentResponse.Error)
        val error = response as GeminiAgentResponse.Error
        assertEquals(GeminiErrorKind.UNAUTHORIZED, error.kind)
        assertFalse(error.retryable)
        assertTrue(error.message.contains("Settings"))
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun badRequestResponse_isNotRetriedAndKeepsServerDetail() = runBlocking {
        val transport = FakeTransport { _, _, _ ->
            GeminiHttpResponse(400, errorBody(400, "INVALID_ARGUMENT", "Invalid function declaration"))
        }

        val response = client(
            transport,
            models = listOf("gemini-3.5-flash-lite", "gemini-3.6-flash")
        ).generateAgentTurn("system", emptyList(), "tools")

        assertTrue(response is GeminiAgentResponse.Error)
        val error = response as GeminiAgentResponse.Error
        assertEquals(GeminiErrorKind.BAD_REQUEST, error.kind)
        assertFalse(error.retryable)
        assertTrue(error.message.contains("Invalid function declaration"))
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun invalidJsonResponse_failsSafely() = runBlocking {
        val transport = FakeTransport { _, _, _ -> GeminiHttpResponse(200, "not-json") }

        val response = client(transport).generateAgentTurn("system", emptyList(), "tools")

        assertTrue(response is GeminiAgentResponse.Error)
        assertEquals(GeminiErrorKind.INVALID_RESPONSE, (response as GeminiAgentResponse.Error).kind)
    }

    @Test
    fun emptyCandidates_failsSafely() = runBlocking {
        val transport = FakeTransport { _, _, _ ->
            GeminiHttpResponse(200, """{"candidates":[]}""")
        }

        val response = client(transport).generateAgentTurn("system", emptyList(), "tools")

        assertTrue(response is GeminiAgentResponse.Error)
        assertEquals(GeminiErrorKind.INVALID_RESPONSE, (response as GeminiAgentResponse.Error).kind)
    }

    @Test
    fun functionCallResponse_isParsedWithRawParts() = runBlocking {
        val body = """{"candidates":[{"content":{"parts":[
            {"functionCall":{"name":"lookup","args":{"query":"x"},"id":"call-1"},"thoughtSignature":"sig"}
        ]},"finishReason":"STOP"}]}"""
        val transport = FakeTransport { _, _, _ -> GeminiHttpResponse(200, body) }

        val response = client(transport).generateAgentTurn("system", emptyList(), "tools")

        assertTrue(response is GeminiAgentResponse.Success)
        val success = response as GeminiAgentResponse.Success
        assertNotNull(success.toolCall)
        assertEquals("lookup", success.toolCall?.toolName)
        assertEquals("call-1", success.toolCall?.id)
        assertTrue(success.modelTurnPartsJson.orEmpty().contains("thoughtSignature"))
    }

    @Test
    fun missingApiKey_failsBeforeAnyNetworkCall() = runBlocking {
        val transport = FakeTransport { _, _, _ -> GeminiHttpResponse(200, successBody("unused")) }
        val client = GeminiAgentClient(
            securePreferences = null,
            transport = transport,
            modelCandidates = listOf("gemini-3.5-flash-lite"),
            apiKeyProvider = { "" },
            fallbackDelayMillis = 0L
        )

        val response = client.generateAgentTurn("system", emptyList(), "tools")

        assertTrue(response is GeminiAgentResponse.Error)
        assertEquals(GeminiErrorKind.MISSING_API_KEY, (response as GeminiAgentResponse.Error).kind)
        assertTrue(transport.requests.isEmpty())
    }
}
