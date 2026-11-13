package com.aurviz.agent.data.remote

import com.aurviz.agent.core.config.GeminiConfig
import com.aurviz.agent.core.security.SecurePreferences
import com.aurviz.agent.domain.model.ToolExecutionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Classification of a failed Gemini call so callers can decide whether to retry. */
enum class GeminiErrorKind {
    MISSING_API_KEY,
    NO_NETWORK,
    TIMEOUT,
    BAD_REQUEST,
    UNAUTHORIZED,
    PERMISSION_DENIED,
    MODEL_NOT_FOUND,
    RATE_LIMITED,
    SERVER_ERROR,
    INVALID_RESPONSE,
    UNKNOWN
}

sealed class GeminiAgentResponse {
    data class Success(
        val text: String,
        val toolCall: ToolExecutionRequest? = null,
        val modelTurnPartsJson: String? = null
    ) : GeminiAgentResponse()

    data class Error(
        val message: String,
        val kind: GeminiErrorKind = GeminiErrorKind.UNKNOWN,
        val statusCode: Int? = null,
        val retryable: Boolean = kind == GeminiErrorKind.RATE_LIMITED ||
            kind == GeminiErrorKind.SERVER_ERROR ||
            kind == GeminiErrorKind.TIMEOUT ||
            kind == GeminiErrorKind.NO_NETWORK,
        val isQuotaExceeded: Boolean = kind == GeminiErrorKind.RATE_LIMITED
    ) : GeminiAgentResponse()
}

/**
 * Gemini REST client for function calling.
 *
 * Model identifiers and endpoint construction live in [GeminiConfig]. A request
 * first targets the configured primary model and only falls back to another stable
 * model when the current model is retired, overloaded, or rate limited.
 */
open class GeminiAgentClient(
    protected open val securePreferences: SecurePreferences? = null,
    private val transport: GeminiHttpTransport = OkHttpGeminiTransport(),
    private val modelCandidates: List<String> = GeminiConfig.modelCandidates(),
    private val apiKeyProvider: () -> String = { securePreferences?.getGeminiApiKey().orEmpty() },
    private val fallbackDelayMillis: Long = 400L
) {

    open suspend fun generateAgentTurn(
        systemInstruction: String,
        conversationHistory: List<GeminiConversationTurn>,
        toolsPrompt: String
    ): GeminiAgentResponse = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) {
            return@withContext GeminiAgentResponse.Error(
                message = "Gemini API key is not configured. Please enter your free API key in Settings.",
                kind = GeminiErrorKind.MISSING_API_KEY,
                retryable = false
            )
        }

        val models = modelCandidates.map(String::trim).filter(String::isNotEmpty).distinct()
        if (models.isEmpty()) {
            return@withContext GeminiAgentResponse.Error(
                message = "No Gemini model is configured. Set GEMINI_MODEL in the build configuration.",
                kind = GeminiErrorKind.UNKNOWN,
                retryable = false
            )
        }

        val requestBodyJson = buildRequestBody(systemInstruction, conversationHistory, toolsPrompt)
        var lastError: GeminiAgentResponse.Error? = null

        for ((index, modelName) in models.withIndex()) {
            val response = executeModel(modelName, apiKey, requestBodyJson.toString())
            when (response) {
                is GeminiAgentResponse.Success -> return@withContext response
                is GeminiAgentResponse.Error -> {
                    lastError = response
                    if (!shouldTryNextModel(response.kind)) {
                        return@withContext response
                    }
                    if (index < models.lastIndex && fallbackDelayMillis > 0) {
                        delay(fallbackDelayMillis)
                    }
                }
            }
        }

        lastError ?: GeminiAgentResponse.Error(
            message = "Gemini did not return a response. Try again later.",
            kind = GeminiErrorKind.UNKNOWN,
            retryable = true
        )
    }

    private fun executeModel(
        modelName: String,
        apiKey: String,
        requestBodyJson: String
    ): GeminiAgentResponse {
        return try {
            val httpResponse = transport.post(
                url = GeminiConfig.generateContentUrl(modelName),
                apiKey = apiKey,
                jsonBody = requestBodyJson
            )
            if (httpResponse.code !in 200..299) {
                errorFor(
                    kind = classifyHttpFailure(httpResponse.code, httpResponse.body),
                    modelName = modelName,
                    statusCode = httpResponse.code,
                    apiMessage = extractApiMessage(httpResponse.body, httpResponse.code)
                )
            } else {
                parseSuccessResponse(httpResponse.body, modelName)
            }
        } catch (error: SocketTimeoutException) {
            networkError("The Gemini request timed out.", GeminiErrorKind.TIMEOUT)
        } catch (error: InterruptedIOException) {
            networkError("The Gemini request timed out.", GeminiErrorKind.TIMEOUT)
        } catch (error: UnknownHostException) {
            networkError("Could not reach Gemini. Check your internet connection.", GeminiErrorKind.NO_NETWORK)
        } catch (error: ConnectException) {
            networkError("Could not connect to Gemini. Check your internet connection.", GeminiErrorKind.NO_NETWORK)
        } catch (error: IOException) {
            networkError(
                "Network error while contacting Gemini (${error.javaClass.simpleName}). Check your connection.",
                GeminiErrorKind.NO_NETWORK
            )
        } catch (error: Exception) {
            GeminiAgentResponse.Error(
                message = "Gemini returned an unreadable response (${error.javaClass.simpleName}).",
                kind = GeminiErrorKind.INVALID_RESPONSE,
                retryable = false
            )
        }
    }

    private fun parseSuccessResponse(body: String, modelName: String): GeminiAgentResponse {
        if (body.isBlank()) {
            return invalidResponse("Gemini returned an empty response body.", modelName)
        }
        val json = try {
            JSONObject(body)
        } catch (error: Exception) {
            return invalidResponse("Gemini returned malformed JSON.", modelName)
        }

        val blockedReason = json.optJSONObject("promptFeedback")
            ?.optString("blockReason")
            ?.takeIf(String::isNotBlank)
        if (blockedReason != null) {
            return GeminiAgentResponse.Error(
                message = "Gemini blocked this request ($blockedReason). Adjust the prompt and try again.",
                kind = GeminiErrorKind.BAD_REQUEST,
                retryable = false
            )
        }

        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            return invalidResponse("Gemini returned no response candidates.", modelName)
        }

        val firstCandidate = candidates.optJSONObject(0)
            ?: return invalidResponse("Gemini returned an unreadable candidate.", modelName)
        val parts = firstCandidate.optJSONObject("content")?.optJSONArray("parts")
            ?: return invalidResponse("Gemini returned an empty response.", modelName)

        val textParts = mutableListOf<String>()
        var nativeToolCall: ToolExecutionRequest? = null
        for (index in 0 until parts.length()) {
            val part = parts.optJSONObject(index) ?: continue
            part.optString("text").takeIf(String::isNotBlank)?.let(textParts::add)
            val call = part.optJSONObject("functionCall")
            if (call != null && nativeToolCall == null) {
                val args = call.optJSONObject("args") ?: JSONObject()
                val map = mutableMapOf<String, Any?>()
                args.keys().forEach { key -> map[key] = args.get(key) }
                val toolName = call.optString("name").takeIf(String::isNotBlank)
                    ?: return invalidResponse("Gemini returned a tool call without a name.", modelName)
                nativeToolCall = ToolExecutionRequest(
                    toolName = toolName,
                    arguments = map,
                    id = call.optString("id").takeIf(String::isNotBlank)
                )
            }
        }

        val rawText = textParts.joinToString("\n").trim()
        if (rawText.isBlank() && nativeToolCall == null) {
            val finishReason = firstCandidate.optString("finishReason").takeIf(String::isNotBlank)
            return invalidResponse(
                if (finishReason != null) {
                    "Gemini returned no text or tool call (finish reason: $finishReason)."
                } else {
                    "Gemini returned an empty response."
                },
                modelName
            )
        }

        return GeminiAgentResponse.Success(
            text = rawText.ifBlank { "I completed the requested action." },
            toolCall = nativeToolCall,
            // Keep model parts verbatim so Gemini 3 thought signatures are echoed back.
            modelTurnPartsJson = parts.toString().takeIf { nativeToolCall != null }
        )
    }

    private fun buildRequestBody(
        systemInstruction: String,
        conversationHistory: List<GeminiConversationTurn>,
        toolsPrompt: String
    ): JSONObject {
        val contentsArray = JSONArray()
        conversationHistory.forEach { turn ->
            val parts = JSONArray()
            val originalPartsJson = turn.rawPartsJson
            if (originalPartsJson != null) {
                val originalParts = runCatching { JSONArray(originalPartsJson) }.getOrNull()
                if (originalParts != null) {
                    for (index in 0 until originalParts.length()) {
                        parts.put(originalParts.get(index))
                    }
                }
            }
            if (parts.length() == 0) {
                turn.text?.let { parts.put(JSONObject().put("text", it)) }
                turn.functionCall?.let { call ->
                    val functionCall = JSONObject()
                        .put("name", call.toolName)
                        .put("args", JSONObject(call.arguments))
                    call.id?.let { functionCall.put("id", it) }
                    parts.put(JSONObject().put("functionCall", functionCall))
                }
                turn.functionResponse?.let { result ->
                    val functionResponse = JSONObject()
                        .put("name", result.name)
                        .put("response", JSONObject().put("result", result.result))
                    result.id?.let { functionResponse.put("id", it) }
                    parts.put(JSONObject().put("functionResponse", functionResponse))
                }
            }
            contentsArray.put(JSONObject().put("role", turn.role).put("parts", parts))
        }

        return JSONObject().apply {
            put("contents", contentsArray)
            put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", "$systemInstruction\n\n$toolsPrompt"))
                )
            )
            put("tools", JSONArray().put(JSONObject().put("functionDeclarations", functionDeclarations())))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("maxOutputTokens", 1024)
            })
        }
    }

    private fun shouldTryNextModel(kind: GeminiErrorKind): Boolean = when (kind) {
        GeminiErrorKind.MODEL_NOT_FOUND,
        GeminiErrorKind.RATE_LIMITED,
        GeminiErrorKind.SERVER_ERROR -> true
        else -> false
    }

    private fun classifyHttpFailure(code: Int, body: String): GeminiErrorKind {
        val error = runCatching { JSONObject(body).optJSONObject("error") }.getOrNull()
        val apiStatus = error?.optString("status").orEmpty()
        val apiMessage = error?.optString("message").orEmpty()
        val normalized = "$apiStatus $apiMessage".lowercase()
        val modelUnavailable = code == 404 ||
            normalized.contains("no longer available") ||
            normalized.contains("not found") ||
            normalized.contains("unsupported model") ||
            normalized.contains("does not exist") ||
            normalized.contains("deprecated")

        return when {
            modelUnavailable -> GeminiErrorKind.MODEL_NOT_FOUND
            code == 400 -> GeminiErrorKind.BAD_REQUEST
            code == 401 -> GeminiErrorKind.UNAUTHORIZED
            code == 403 -> GeminiErrorKind.PERMISSION_DENIED
            code == 429 || apiStatus.equals("RESOURCE_EXHAUSTED", ignoreCase = true) ->
                GeminiErrorKind.RATE_LIMITED
            code in 500..599 -> GeminiErrorKind.SERVER_ERROR
            else -> GeminiErrorKind.UNKNOWN
        }
    }

    private fun extractApiMessage(body: String, code: Int): String {
        val message = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull()
        return message?.takeIf(String::isNotBlank) ?: "HTTP $code"
    }

    private fun errorFor(
        kind: GeminiErrorKind,
        modelName: String,
        statusCode: Int,
        apiMessage: String
    ): GeminiAgentResponse.Error {
        val detail = apiMessage.takeIf { it.isNotBlank() && !it.equals("HTTP $statusCode", ignoreCase = true) }
        val message = when (kind) {
            GeminiErrorKind.MODEL_NOT_FOUND ->
                "Gemini model '$modelName' is no longer available (HTTP $statusCode). " +
                    "Update GEMINI_MODEL to a supported model such as ${GeminiConfig.DEFAULT_MODEL}."
            GeminiErrorKind.UNAUTHORIZED ->
                "Gemini rejected the API key (HTTP 401). Update the Gemini API key in Settings."
            GeminiErrorKind.PERMISSION_DENIED ->
                "Gemini denied access to model '$modelName' (HTTP 403). Check API key restrictions and model access."
            GeminiErrorKind.BAD_REQUEST ->
                "Gemini rejected the request (HTTP 400)." + (detail?.let { " $it" } ?: "")
            GeminiErrorKind.RATE_LIMITED ->
                "Gemini rate limit exceeded (HTTP 429). The task will retry later." +
                    (detail?.let { " $it" } ?: "")
            GeminiErrorKind.SERVER_ERROR ->
                "Gemini is temporarily unavailable (HTTP $statusCode). Try again later."
            else ->
                "Gemini request failed (HTTP $statusCode)." + (detail?.let { " $it" } ?: "")
        }
        return GeminiAgentResponse.Error(
            message = message,
            kind = kind,
            statusCode = statusCode
        )
    }

    private fun invalidResponse(message: String, modelName: String) = GeminiAgentResponse.Error(
        message = "$message Model: $modelName.",
        kind = GeminiErrorKind.INVALID_RESPONSE,
        retryable = false
    )

    private fun networkError(message: String, kind: GeminiErrorKind) = GeminiAgentResponse.Error(
        message = message,
        kind = kind,
        retryable = true
    )

    private fun functionDeclarations(): JSONArray {
        fun property(type: String, description: String) = JSONObject().put("type", type).put("description", description)
        fun declaration(name: String, description: String, properties: JSONObject, required: List<String>) =
            JSONObject().put("name", name).put("description", description)
                .put("parameters", JSONObject().put("type", "OBJECT").put("properties", properties)
                    .put("required", JSONArray(required)))

        return JSONArray()
            .put(declaration("manage_memory", "Save, recall, update, or delete user facts and preferences.", JSONObject()
                .put("action", property("STRING", "save, recall, update, or delete"))
                .put("key", property("STRING", "Short memory key; identifies the memory for update/delete"))
                .put("content", property("STRING", "Fact to save, or the new value for update"))
                .put("category", property("STRING", "Memory category"))
                .put("query", property("STRING", "Recall search terms")), listOf("action")))
            .put(declaration("schedule_task", "Schedule a daily recurring or one-time background task.", JSONObject()
                .put("title", property("STRING", "Concise task title"))
                .put("promptAction", property("STRING", "What the agent should do when it runs"))
                .put("timeExpression", property("STRING", "Local time, for example 9:00 AM"))
                .put("isRecurring", property("BOOLEAN", "True for daily recurrence")), listOf("promptAction", "timeExpression", "isRecurring")))
            .put(declaration("browse_web", "Fetch and extract public web page text or headlines.", JSONObject()
                .put("url", property("STRING", "Public HTTP or HTTPS URL"))
                .put("action", property("STRING", "Extraction type")), listOf("url")))
            .put(declaration("search_emails", "Search the user's Gmail inbox. Use Gmail query syntax, e.g. 'is:important newer_than:1d', 'subject:interview newer_than:7d', 'from:hr@example.com'.", JSONObject()
                .put("query", property("STRING", "Gmail search query"))
                .put("maxResults", property("INTEGER", "Maximum emails to return, 1-15 (default 10)")), listOf("query")))
            .put(declaration("read_email", "Read the full body of one Gmail message using an id from search_emails.", JSONObject()
                .put("messageId", property("STRING", "Message id from search_emails")), listOf("messageId")))
            .put(declaration("create_email_draft", "Save an email to Gmail Drafts without sending. Use when the user says draft/prepare/write but not send, or says don't send.", JSONObject()
                .put("recipient", property("STRING", "Recipient email address"))
                .put("subject", property("STRING", "Email subject"))
                .put("body", property("STRING", "Email body")), listOf("recipient", "subject", "body")))
            .put(declaration("send_email", "Send an email using Gmail only after the user reviews and explicitly approves the recipient, subject, and body.", JSONObject()
                .put("recipient", property("STRING", "Recipient email address"))
                .put("subject", property("STRING", "Email subject"))
                .put("body", property("STRING", "Email body")), listOf("recipient", "subject", "body")))
            .put(declaration("open_url", "Open a public HTTPS website in the controlled in-app browser.", JSONObject()
                .put("url", property("STRING", "Public HTTPS URL")), listOf("url")))
            .put(declaration("search_webpage", "Search the current page using its search input, or search the web if no page is open.", JSONObject()
                .put("query", property("STRING", "Search terms"))
                .put("selector", property("STRING", "Optional CSS selector for the page search field")), listOf("query")))
            .put(declaration("read_page", "Read visible text from the active browser page.", JSONObject(), emptyList()))
            .put(declaration("click_element", "Click a page element. Requires user approval before the click.", JSONObject()
                .put("selector", property("STRING", "CSS selector for the element")), listOf("selector")))
            .put(declaration("enter_text", "Enter text into a page field without submitting it. Requires user approval.", JSONObject()
                .put("selector", property("STRING", "CSS selector for an editable field"))
                .put("text", property("STRING", "Text to enter")), listOf("selector", "text")))
            .put(declaration("scroll_page", "Scroll the active browser page up or down.", JSONObject()
                .put("direction", property("STRING", "up or down")), listOf("direction")))
            .put(declaration("navigate_back", "Go to the previous browser history entry.", JSONObject(), emptyList()))
            .put(declaration("navigate_forward", "Go to the next browser history entry.", JSONObject(), emptyList()))
            .put(declaration("get_browser_state", "Return current browser page title, URL, loading state, and text preview.", JSONObject(), emptyList()))
    }
}
