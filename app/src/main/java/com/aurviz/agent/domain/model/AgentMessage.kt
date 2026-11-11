package com.aurviz.agent.domain.model

sealed class AgentMessage(
    open val id: String = java.util.UUID.randomUUID().toString(),
    open val timestamp: Long = System.currentTimeMillis()
) {
    data class UserPrompt(
        override val id: String = java.util.UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val text: String,
        val isVoiceInput: Boolean = false
    ) : AgentMessage(id, timestamp)

    data class AgentReasoning(
        override val id: String = java.util.UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val thought: String
    ) : AgentMessage(id, timestamp)

    data class ToolAction(
        override val id: String = java.util.UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val toolName: String,
        val argumentsJson: String,
        val status: ToolStatus = ToolStatus.RUNNING,
        val resultOutput: String? = null
    ) : AgentMessage(id, timestamp)

    data class AgentResponse(
        override val id: String = java.util.UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val text: String
    ) : AgentMessage(id, timestamp)

    data class ErrorMessage(
        override val id: String = java.util.UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val error: String
    ) : AgentMessage(id, timestamp)
}

enum class ToolStatus {
    RUNNING,
    AWAITING_CONFIRMATION,
    CONFIRMED,
    CANCELLED,
    SUCCESS,
    FAILED
}
