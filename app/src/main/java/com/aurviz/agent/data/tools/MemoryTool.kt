package com.aurviz.agent.data.tools

import com.aurviz.agent.domain.model.Memory
import com.aurviz.agent.domain.model.ToolExecutionResponse
import com.aurviz.agent.domain.repository.MemoryRepository
import com.aurviz.agent.domain.tool.AgentTool

/**
 * Save, recall, update and delete user memories. Update and delete only act on a
 * single unambiguous match; otherwise the candidates are returned so the agent can
 * ask the user which one they meant.
 */
class MemoryTool(
    private val memoryRepository: MemoryRepository
) : AgentTool {

    override val name: String = "manage_memory"
    override val description: String =
        "Save, recall, update, or delete facts and user preferences. Actions: 'save', 'recall', 'update', 'delete'."

    override suspend fun execute(arguments: Map<String, Any?>): ToolExecutionResponse {
        val action = (arguments["action"] as? String ?: "save").lowercase().trim()

        return when (action) {
            "save" -> {
                val key = (arguments["key"] as? String)?.trim().orEmpty().ifBlank { "note" }
                val content = (arguments["content"] as? String)?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: return failure("Missing 'content' parameter to save.")
                val category = (arguments["category"] as? String)?.trim().orEmpty().ifBlank { "general" }

                // Saving a key that already exists replaces it instead of storing a duplicate.
                val existing = findMatches(key).firstOrNull { it.key.equals(key, ignoreCase = true) }
                val id = memoryRepository.insertMemory(
                    existing?.copy(content = content, category = category, createdAt = System.currentTimeMillis())
                        ?: Memory(key = key, content = content, category = category)
                )
                val verb = if (existing != null) "updated" else "saved"
                ToolExecutionResponse(name, true, "Memory $verb (ID: $id): [$key] $content")
            }

            "recall" -> {
                val query = (arguments["query"] as? String)?.trim().orEmpty()
                val memories = if (query.isBlank()) {
                    memoryRepository.getRecentMemories(5)
                } else {
                    findMatches(query)
                }
                if (memories.isEmpty()) {
                    ToolExecutionResponse(name, true, "No matching memories found for query '$query'.")
                } else {
                    ToolExecutionResponse(name, true, "Found memories:\n" + format(memories))
                }
            }

            "update" -> {
                val content = (arguments["content"] as? String)?.trim()?.takeIf(String::isNotEmpty)
                    ?: return failure("Missing 'content' with the new value.")
                when (val target = resolveSingle(arguments)) {
                    is Resolution.Found -> {
                        val updated = target.memory.copy(
                            content = content,
                            category = (arguments["category"] as? String)?.trim()?.takeIf(String::isNotEmpty)
                                ?: target.memory.category,
                            createdAt = System.currentTimeMillis()
                        )
                        memoryRepository.insertMemory(updated)
                        ToolExecutionResponse(name, true, "Memory updated: [${updated.key}] ${updated.content}")
                    }
                    is Resolution.Problem -> failure(target.message)
                }
            }

            "delete", "forget", "remove" -> when (val target = resolveSingle(arguments)) {
                is Resolution.Found -> {
                    memoryRepository.deleteMemory(target.memory.id)
                    ToolExecutionResponse(name, true, "Memory deleted: [${target.memory.key}] ${target.memory.content}")
                }
                is Resolution.Problem -> failure(target.message)
            }

            else -> failure("Unknown memory action: $action. Use save, recall, update, or delete.")
        }
    }

    private suspend fun findMatches(query: String): List<Memory> =
        memoryRepository.searchMemories(query.trim())

    private sealed class Resolution {
        data class Found(val memory: Memory) : Resolution()
        data class Problem(val message: String) : Resolution()
    }

    private suspend fun resolveSingle(arguments: Map<String, Any?>): Resolution {
        val query = listOf(arguments["key"], arguments["query"])
            .mapNotNull { (it as? String)?.trim() }
            .firstOrNull(String::isNotEmpty)
            ?: return Resolution.Problem("Provide 'key' or 'query' identifying the memory.")
        val matches = findMatches(query)
        val exact = matches.filter { it.key.equals(query, ignoreCase = true) }
        return when {
            exact.size == 1 -> Resolution.Found(exact.single())
            matches.size == 1 -> Resolution.Found(matches.single())
            matches.isEmpty() -> Resolution.Problem("No memory matches '$query'.")
            else -> Resolution.Problem(
                "Several memories match '$query'. Ask the user which one they mean:\n" + format(matches.take(8))
            )
        }
    }

    private fun format(memories: List<Memory>) =
        memories.joinToString("\n") { "• [${it.category}] ${it.key}: ${it.content}" }

    private fun failure(message: String) = ToolExecutionResponse(name, false, message)
}
