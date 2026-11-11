package com.aurviz.agent.data.local.memory

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aurviz.agent.domain.model.Memory

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val key: String,
    val content: String,
    val category: String,
    val createdAt: Long,
    val importance: Int
) {
    fun toDomain(): Memory = Memory(
        id = id,
        key = key,
        content = content,
        category = category,
        createdAt = createdAt,
        importance = importance
    )

    companion object {
        fun fromDomain(memory: Memory): MemoryEntity = MemoryEntity(
            id = memory.id,
            key = memory.key,
            content = memory.content,
            category = memory.category,
            createdAt = memory.createdAt,
            importance = memory.importance
        )
    }
}
