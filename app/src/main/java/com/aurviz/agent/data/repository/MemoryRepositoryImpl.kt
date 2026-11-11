package com.aurviz.agent.data.repository

import com.aurviz.agent.data.local.memory.MemoryDao
import com.aurviz.agent.data.local.memory.MemoryEntity
import com.aurviz.agent.domain.model.Memory
import com.aurviz.agent.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MemoryRepositoryImpl(
    private val memoryDao: MemoryDao
) : MemoryRepository {

    override fun getAllMemories(): Flow<List<Memory>> {
        return memoryDao.getAllMemories().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertMemory(memory: Memory): Long {
        return memoryDao.insertMemory(MemoryEntity.fromDomain(memory))
    }

    override suspend fun deleteMemory(id: Long) {
        memoryDao.deleteMemory(id)
    }

    override suspend fun searchMemories(query: String): List<Memory> {
        return memoryDao.searchMemories(query).map { it.toDomain() }
    }

    override suspend fun getRecentMemories(limit: Int): List<Memory> {
        return memoryDao.getRecentMemories(limit).map { it.toDomain() }
    }
}
