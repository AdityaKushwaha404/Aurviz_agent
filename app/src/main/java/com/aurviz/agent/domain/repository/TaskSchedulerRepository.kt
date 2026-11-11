package com.aurviz.agent.domain.repository

import com.aurviz.agent.domain.model.ScheduledTask
import kotlinx.coroutines.flow.Flow

interface TaskSchedulerRepository {
    fun getAllTasks(): Flow<List<ScheduledTask>>
    suspend fun getTaskById(id: String): ScheduledTask?
    suspend fun scheduleTask(task: ScheduledTask)
    suspend fun cancelTask(id: String)
    suspend fun triggerTaskNow(id: String): String
    suspend fun updateTaskStatus(id: String, lastExecutionEpoch: Long, status: String)
}
