package com.aurviz.agent.data.local.history

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.aurviz.agent.domain.model.TaskExecutionRecord
import kotlinx.coroutines.flow.Flow

/** One persisted run of a scheduled task (success, failure, retry or skip). */
@Entity(tableName = "task_executions", indices = [Index(value = ["taskId", "startedAt"])])
data class TaskExecutionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: String,
    val taskTitle: String,
    val startedAt: Long,
    val finishedAt: Long,
    val status: String,
    val message: String,
    val attempt: Int
) {
    fun toDomain() = TaskExecutionRecord(id, taskId, taskTitle, startedAt, finishedAt, status, message, attempt)
}

@Dao
interface TaskExecutionDao {
    @Insert
    suspend fun insert(execution: TaskExecutionEntity)

    @Query("SELECT * FROM task_executions ORDER BY startedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<TaskExecutionEntity>>

    @Query("DELETE FROM task_executions WHERE id NOT IN (SELECT id FROM task_executions ORDER BY startedAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int)

    @Query("DELETE FROM task_executions WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: String)
}
