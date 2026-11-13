package com.aurviz.agent.domain.model

data class ToolExecutionRequest(
    val toolName: String,
    val arguments: Map<String, Any?>,
    val id: String? = null
)

data class ToolExecutionResponse(
    val toolName: String,
    val isSuccess: Boolean,
    val output: String,
    val sensitiveActionRequired: SensitiveAction? = null,
    /** True when the failure is transient (network, rate limit) and the operation may be retried. */
    val retryable: Boolean = false
)
