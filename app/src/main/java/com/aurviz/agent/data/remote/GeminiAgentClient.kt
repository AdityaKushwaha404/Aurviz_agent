package com.aurviz.agent.data.remote

import com.aurviz.agent.core.security.SecurePreferences
import com.aurviz.agent.domain.model.ToolExecutionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class GeminiAgentResponse {
    data class Success(
        val text: String,
        val toolCall: ToolExecutionRequest? = null,
        val modelTurnPartsJson: String? = null
    ) : GeminiAgentResponse()
    data class Error(val message: String, val isQuotaExceeded: Boolean = false) : GeminiAgentResponse()
}

open class GeminiAgentClient(
    protected open val securePreferences: SecurePreferences? = null
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    open suspend fun generateAgentTurn(
        systemInstruction: String,
        conversationHistory: List<GeminiConversationTurn>,
        toolsPrompt: String
    ): GeminiAgentResponse = withContext(Dispatchers.IO) {
        val apiKey = securePreferences?.getGeminiApiKey() ?: ""
        if (apiKey.isBlank()) {
            return@withContext GeminiAgentResponse.Error(
                "Gemini API key is not configured. Please enter your free API key in Settings."
            )
        }

        // Use the strongest currently free-tier model first, then compatible fallbacks.
        val candidateModels = listOf(
            "gemini-3.8-flash",
            "gemini-3.1-flash-lite",
            "gemini-2.5-flash"
        )
        var lastErrorMsg = "Unable to connect to Gemini API."

        for (modelName in candidateModels) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"

                val contentsArray = JSONArray()

                // Conversation turns
                conversationHistory.forEach { turn ->
                    val parts = JSONArray()
                    val originalPartsJson = turn.rawPartsJson
                    if (originalPartsJson != null) {
                        val originalParts = JSONArray(originalPartsJson)
                        for (index in 0 until originalParts.length()) {
                            parts.put(originalParts.get(index))
                        }
                    } else {
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
                    contentsArray.put(
                        JSONObject().put("role", turn.role).put("parts", parts)
                    )
                }

                val requestBodyJson = JSONObject().apply {
                    put("contents", contentsArray)
                    put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "$systemInstruction\n\n$toolsPrompt"))))
                    put("tools", JSONArray().put(JSONObject().put("functionDeclarations", functionDeclarations())))
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.3)
                        put("maxOutputTokens", 1024)
                    })
                }

                val request = Request.Builder()
                    .url(url)
                    .header("x-goog-api-key", apiKey)
                    .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    if (response.code == 429) {
                        return@withContext GeminiAgentResponse.Error(
                            "Gemini API rate limit exceeded (HTTP 429). Please wait a moment before trying again.",
                            isQuotaExceeded = true
                        )
                    }
                    val errorMsg = try {
                        JSONObject(responseBody).getJSONObject("error").getString("message")
                    } catch (e: Exception) {
                        "HTTP ${response.code}: ${response.message}"
                    }
                    // If model not found, try next candidate model
                    if (response.code == 404 || errorMsg.contains("not found", ignoreCase = true)) {
                        lastErrorMsg = errorMsg
                        continue
                    }
                    return@withContext GeminiAgentResponse.Error("Gemini Error: $errorMsg")
                }

                val json = JSONObject(responseBody)
                val candidates = json.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    return@withContext GeminiAgentResponse.Error("No response candidates returned by Gemini.")
                }

                val firstCandidate = candidates.getJSONObject(0)
                val parts = firstCandidate.optJSONObject("content")?.optJSONArray("parts")
                    ?: return@withContext GeminiAgentResponse.Error("Gemini returned an empty response.")
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
                        nativeToolCall = ToolExecutionRequest(
                            toolName = call.getString("name"),
                            arguments = map,
                            id = call.optString("id").takeIf(String::isNotBlank)
                        )
                    }
                }
                val rawText = textParts.joinToString("\n").ifBlank { "I completed the requested action." }
                // Keep Gemini's complete model parts intact: Gemini 3 function calling
                // requires its thoughtSignature to be echoed in the next request.
                return@withContext GeminiAgentResponse.Success(
                    text = rawText,
                    toolCall = nativeToolCall,
                    modelTurnPartsJson = parts.toString().takeIf { nativeToolCall != null }
                )
            } catch (e: Exception) {
                lastErrorMsg = "Network/API request failed (${e.javaClass.simpleName})."
            }
        }

        GeminiAgentResponse.Error("Gemini Error: $lastErrorMsg")
    }

    private fun functionDeclarations(): JSONArray {
        fun property(type: String, description: String) = JSONObject().put("type", type).put("description", description)
        fun declaration(name: String, description: String, properties: JSONObject, required: List<String>) =
            JSONObject().put("name", name).put("description", description)
                .put("parameters", JSONObject().put("type", "OBJECT").put("properties", properties)
                    .put("required", JSONArray(required)))

        return JSONArray()
            .put(declaration("manage_memory", "Save or recall user facts and preferences.", JSONObject()
                .put("action", property("STRING", "save or recall"))
                .put("key", property("STRING", "Short memory key"))
                .put("content", property("STRING", "Fact to save"))
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
