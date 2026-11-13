package com.aurviz.agent.data.tools

import com.aurviz.agent.domain.model.Memory
import com.aurviz.agent.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryToolTest {

    private class InMemoryRepo : MemoryRepository {
        val rows = linkedMapOf<Long, Memory>()
        private var nextId = 1L
        override fun getAllMemories(): Flow<List<Memory>> = flowOf(rows.values.toList())
        override suspend fun insertMemory(memory: Memory): Long {
            val id = if (memory.id == 0L) nextId++ else memory.id
            rows[id] = memory.copy(id = id)
            return id
        }
        override suspend fun deleteMemory(id: Long) { rows.remove(id) }
        override suspend fun searchMemories(query: String): List<Memory> = rows.values.filter {
            it.key.contains(query, true) || it.content.contains(query, true) || it.category.contains(query, true)
        }
        override suspend fun getRecentMemories(limit: Int): List<Memory> = rows.values.toList().takeLast(limit)
    }

    private val repo = InMemoryRepo()
    private val tool = MemoryTool(repo)

    @Test
    fun saveSameKeyTwice_updatesInsteadOfDuplicating() = runBlocking {
        tool.execute(mapOf("action" to "save", "key" to "email_tone", "content" to "casual"))
        val result = tool.execute(mapOf("action" to "save", "key" to "email_tone", "content" to "formal"))

        assertTrue(result.output.contains("updated"))
        assertEquals(1, repo.rows.size)
        assertEquals("formal", repo.rows.values.single().content)
    }

    @Test
    fun updateAndDelete_actOnSingleMatch() = runBlocking {
        tool.execute(mapOf("action" to "save", "key" to "manager", "content" to "Alex, alex@co.com"))

        val updated = tool.execute(mapOf("action" to "update", "key" to "manager", "content" to "Sam, sam@co.com"))
        assertTrue(updated.isSuccess)
        assertEquals("Sam, sam@co.com", repo.rows.values.single().content)

        val deleted = tool.execute(mapOf("action" to "delete", "key" to "manager"))
        assertTrue(deleted.isSuccess)
        assertTrue(repo.rows.isEmpty())
    }

    @Test
    fun ambiguousDelete_listsCandidatesAndDeletesNothing() = runBlocking {
        tool.execute(mapOf("action" to "save", "key" to "rahul_work", "content" to "rahul@work.com"))
        tool.execute(mapOf("action" to "save", "key" to "rahul_personal", "content" to "rahul@home.com"))

        val result = tool.execute(mapOf("action" to "delete", "query" to "rahul"))

        assertFalse(result.isSuccess)
        assertTrue(result.output.contains("Several memories"))
        assertEquals(2, repo.rows.size)
    }

    @Test
    fun recallFindsSavedPreference() = runBlocking {
        tool.execute(mapOf("action" to "save", "key" to "email_tone", "content" to "I prefer formal emails"))
        val result = tool.execute(mapOf("action" to "recall", "query" to "formal"))
        assertTrue(result.output.contains("I prefer formal emails"))
    }
}
