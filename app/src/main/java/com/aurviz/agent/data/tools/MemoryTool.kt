package com.aurviz.agent.data.tools

import com.aurviz.agent.domain.model.Memory
import com.aurviz.agent.domain.model.ToolExecutionResponse
import com.aurviz.agent.domain.repository.MemoryRepository
import com.aurviz.agent.domain.tool.AgentTool

class MemoryTool(
    private val memoryRepository: MemoryRepository
) : AgentTool {

    override val name: String = "manage_memory"
    override val description: String = "Save or recall facts, user preferences, and important information. Actions: 'save' or 'recall'."

    override suspend fun execute(arguments: Map<String, Any?>): ToolExecutionResponse {
        val action = arguments["action"] as? String ?: "save"

        return when (action.lowercase()) {
            "save" -> {
                val key = arguments["key"] as? String ?: "note"
                val content = arguments["content"] as? String
                    ?: return ToolExecutionResponse(name, false, "Missing 'content' parameter to save.")
                val category = arguments["category"] as? String ?: "general"

                val id = memoryRepository.insertMemory(
                    Memory(key = key, content = content, category = category)
                )
                ToolExecutionResponse(name, true, "Memory successfully saved (ID: $id): [$key] $content")
            }
            "recall" -> {
                val query = arguments["query"] as? String ?: ""
                val memories = if (query.isBlank()) {
                    memoryRepository.getRecentMemories(5)
                } else {
                    memoryRepository.searchMemories(query)
                }

                if (memories.isEmpty()) {
                    ToolExecutionResponse(name, true, "No matching memories found for query '$query'.")
                } else {
                    val formatted = memories.joinToString("\n") { "• [${it.category}] ${it.key}: ${it.content}" }
                    ToolExecutionResponse(name, true, "Found memories:\n$formatted")
                }
            }
            else -> ToolExecutionResponse(name, false, "Unknown memory action: $action")
        }
    }
}
