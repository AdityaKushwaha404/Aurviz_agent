package com.aurviz.agent.core.config

import com.aurviz.agent.BuildConfig

/**
 * Single source of truth for the Gemini REST endpoint and model identifiers.
 *
 * The model can be changed without touching any service code:
 * set `GEMINI_MODEL` in `app/build.gradle.kts` (`buildConfigField`) or update
 * [DEFAULT_MODEL]. Only stable, officially supported model identifiers belong here.
 *
 * See https://ai.google.dev/gemini-api/docs/models
 */
object GeminiConfig {

    /** REST API version used by `generateContent`. */
    const val API_VERSION = "v1beta"

    /** Gemini API host, without a trailing slash. */
    const val BASE_URL = "https://generativelanguage.googleapis.com"

    /**
     * Current stable low-latency model. Models are retired over time; keep this in
     * sync with the official model list.
     */
    const val DEFAULT_MODEL = "gemini-3.5-flash-lite"

    /**
     * Stable models tried in order when the primary model is retired, temporarily
     * overloaded, rate limited, or unavailable for the configured API key.
     */
    val FALLBACK_MODELS: List<String> = listOf(
        "gemini-3.6-flash",
        "gemini-3.1-flash-lite"
    )

    /** Currently configured primary model, taken from the build configuration. */
    fun primaryModel(): String = BuildConfig.GEMINI_MODEL.trim().ifBlank { DEFAULT_MODEL }

    /** Ordered, de-duplicated list of models to attempt for one request. */
    fun modelCandidates(): List<String> = (listOf(primaryModel()) + FALLBACK_MODELS)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

    /** Builds the `generateContent` endpoint for [model]. */
    fun generateContentUrl(model: String, baseUrl: String = BASE_URL): String =
        "${baseUrl.trimEnd('/')}/$API_VERSION/models/$model:generateContent"
}
