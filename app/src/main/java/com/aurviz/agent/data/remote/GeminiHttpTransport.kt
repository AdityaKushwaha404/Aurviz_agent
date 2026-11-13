package com.aurviz.agent.data.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Raw HTTP result from a Gemini `generateContent` call. */
data class GeminiHttpResponse(
    val code: Int,
    val body: String
)

/**
 * Small transport abstraction around the Gemini HTTP call. It keeps
 * [GeminiAgentClient] unit-testable without a live network and without
 * consuming real API quota.
 */
fun interface GeminiHttpTransport {
    fun post(url: String, apiKey: String, jsonBody: String): GeminiHttpResponse
}

/** Default OkHttp-backed transport with bounded connect, read and call timeouts. */
class OkHttpGeminiTransport(
    private val client: OkHttpClient = defaultClient()
) : GeminiHttpTransport {

    override fun post(url: String, apiKey: String, jsonBody: String): GeminiHttpResponse {
        val request = Request.Builder()
            .url(url)
            // The key travels in a header, never in the URL, so it cannot leak into logs.
            .header("x-goog-api-key", apiKey)
            .post(jsonBody.toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { response ->
            return GeminiHttpResponse(
                code = response.code,
                body = response.body?.string().orEmpty()
            )
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
