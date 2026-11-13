package com.aurviz.agent.domain.tool

import com.aurviz.agent.domain.model.ToolExecutionResponse

interface AgentTool {
    val name: String
    val description: String
    val isSensitive: Boolean get() = false

    /** True for tools that need a visible screen (for example the browser), so they cannot run in the background. */
    val requiresForeground: Boolean get() = false

    /**
     * Executes the tool with the provided arguments.
     * If [isSensitive] is true and not yet confirmed, returns a ToolExecutionResponse with [sensitiveActionRequired].
     */
    suspend fun execute(arguments: Map<String, Any?>): ToolExecutionResponse

    /**
     * Executes the confirmed action after explicit user approval.
     */
    suspend fun executeConfirmed(actionId: String, payload: Map<String, Any?>): ToolExecutionResponse {
        return execute(payload)
    }
}
