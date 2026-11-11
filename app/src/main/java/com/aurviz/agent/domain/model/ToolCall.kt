package com.aurviz.agent.domain.model

data class ToolExecutionRequest(
    val toolName: String,
    val arguments: Map<String, Any?>
)

data class ToolExecutionResponse(
    val toolName: String,
    val isSuccess: Boolean,
    val output: String,
    val sensitiveActionRequired: SensitiveAction? = null
)
