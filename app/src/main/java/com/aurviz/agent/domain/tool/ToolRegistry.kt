package com.aurviz.agent.domain.tool

class ToolRegistry(private val tools: List<AgentTool>) {

    private val toolMap = tools.associateBy { it.name }

    fun getTool(name: String): AgentTool? = toolMap[name]

    fun getAllTools(): List<AgentTool> = tools

    fun getSystemPromptToolDescriptions(): String {
        return buildString {
            appendLine("Available Tools:")
            tools.forEach { tool ->
                appendLine("- ${tool.name}: ${tool.description} (Requires Confirmation: ${tool.isSensitive})")
            }
        }
    }
}
