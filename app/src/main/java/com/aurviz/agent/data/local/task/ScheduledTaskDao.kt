package com.aurviz.agent.data.local.task

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledTaskDao {

    @Query("SELECT * FROM scheduled_tasks ORDER BY nextRunEpoch ASC")
    fun getAllTasks(): Flow<List<ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id")
    suspend fun getTaskById(id: String): ScheduledTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: ScheduledTaskEntity)

    @Query("DELETE FROM scheduled_tasks WHERE id = :id")
    suspend fun deleteTask(id: String)

    @Query("UPDATE scheduled_tasks SET lastExecutionEpoch = :epoch, lastExecutionStatus = :status, nextRunEpoch = :nextRunEpoch, isEnabled = :enabled WHERE id = :id")
    suspend fun updateExecutionStatus(id: String, epoch: Long, status: String, nextRunEpoch: Long, enabled: Boolean)

    @Query("UPDATE scheduled_tasks SET isEnabled = :enabled WHERE id = :id")
    suspend fun updateTaskEnabled(id: String, enabled: Boolean)
}
