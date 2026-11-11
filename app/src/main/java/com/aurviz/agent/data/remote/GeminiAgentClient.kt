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
    data class Success(val text: String, val toolCall: ToolExecutionRequest? = null) : GeminiAgentResponse()
    data class Error(val message: String, val isQuotaExceeded: Boolean = false) : GeminiAgentResponse()
}

class GeminiAgentClient(
    private val securePreferences: SecurePreferences
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    suspend fun generateAgentTurn(
        systemInstruction: String,
        conversationHistory: List<Pair<String, String>>, // role ("user" | "model"), text
        toolsPrompt: String
    ): GeminiAgentResponse = withContext(Dispatchers.IO) {
        val apiKey = securePreferences.getGeminiApiKey()
        if (apiKey.isBlank()) {
            return@withContext GeminiAgentResponse.Error(
                "Gemini API key is not configured. Please enter your free API key in Settings."
            )
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

            val contentsArray = JSONArray()

            // System instruction as first context if needed
            val fullSystemPrompt = "$systemInstruction\n\n$toolsPrompt\n\n" +
                    "CRITICAL INSTRUCTIONS:\n" +
                    "You are Aurviz AI Agent. If you need to execute an action, respond with a valid JSON block inside ```json and ``` with the following structure:\n" +
                    "{\n" +
                    "  \"thought\": \"Brief explanation of your reasoning\",\n" +
                    "  \"tool\": \"tool_name\",\n" +
                    "  \"arguments\": { ... }\n" +
                    "}\n" +
                    "If no tool is needed or after receiving tool results, provide your final response to the user."

            val systemPart = JSONObject().put("text", fullSystemPrompt)
            contentsArray.put(
                JSONObject().put("role", "user").put("parts", JSONArray().put(systemPart))
            )
            contentsArray.put(
                JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("text", "Understood. I will act as Aurviz Personal AI Agent, reason step-by-step, and output tool actions in structured JSON when needed.")))
            )

            // Conversation turns
            conversationHistory.forEach { (role, message) ->
                val part = JSONObject().put("text", message)
                contentsArray.put(
                    JSONObject().put("role", role).put("parts", JSONArray().put(part))
                )
            }

            val requestBodyJson = JSONObject().apply {
                put("contents", contentsArray)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                    put("maxOutputTokens", 1024)
                })
            }

            val request = Request.Builder()
                .url(url)
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
                return@withContext GeminiAgentResponse.Error("Gemini Error: $errorMsg")
            }

            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext GeminiAgentResponse.Error("No response candidates returned by Gemini.")
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val rawText = parts.getJSONObject(0).getString("text")

            // Parse for tool execution JSON block
            val toolCall = parseToolCallJson(rawText)

            GeminiAgentResponse.Success(rawText, toolCall)
        } catch (e: Exception) {
            GeminiAgentResponse.Error("Network/API Exception: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    private fun parseToolCallJson(text: String): ToolExecutionRequest? {
        val jsonPattern = Regex("""```(?:json)?\s*(\{[\s\S]*?\})\s*```""", RegexOption.IGNORE_CASE)
        val match = jsonPattern.find(text)
        val jsonString = match?.groupValues?.get(1) ?: if (text.trim().startsWith("{") && text.trim().endsWith("}")) text.trim() else null

        if (jsonString != null) {
            try {
                val obj = JSONObject(jsonString)
                if (obj.has("tool")) {
                    val toolName = obj.getString("tool")
                    val argsObj = obj.optJSONObject("arguments") ?: JSONObject()
                    val argsMap = mutableMapOf<String, Any?>()
                    argsObj.keys().forEach { key ->
                        argsMap[key] = argsObj.get(key)
                    }
                    return ToolExecutionRequest(toolName, argsMap)
                }
            } catch (e: Exception) {
                // Not a valid tool call JSON, treat as standard text
            }
        }
        return null
    }
}
