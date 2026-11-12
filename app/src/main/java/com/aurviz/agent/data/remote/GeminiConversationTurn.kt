package com.aurviz.agent.data.remote

import com.aurviz.agent.domain.model.ToolExecutionRequest

data class GeminiConversationTurn(
    val role: String,
    val text: String? = null,
    val functionCall: ToolExecutionRequest? = null,
    val functionResponse: GeminiFunctionResponse? = null,
    /** Original model parts, retained verbatim so Gemini thought signatures are returned unchanged. */
    val rawPartsJson: String? = null
)

data class GeminiFunctionResponse(
    val name: String,
    val result: String,
    val id: String? = null
)
