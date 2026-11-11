package com.aurviz.agent.data.local.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemory(id: Long)

    @Query("""
        SELECT * FROM memories 
        WHERE `key` LIKE '%' || :query || '%' 
           OR content LIKE '%' || :query || '%' 
           OR category LIKE '%' || :query || '%'
        ORDER BY importance DESC, createdAt DESC
    """)
    suspend fun searchMemories(query: String): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentMemories(limit: Int): List<MemoryEntity>
}
