package com.aurviz.agent.domain.repository

import com.aurviz.agent.domain.model.ScheduledTask
import com.aurviz.agent.domain.model.TaskExecutionRecord
import kotlinx.coroutines.flow.Flow

interface TaskSchedulerRepository {
    fun getAllTasks(): Flow<List<ScheduledTask>>
    suspend fun getTaskById(id: String): ScheduledTask?
    suspend fun scheduleTask(task: ScheduledTask)
    suspend fun updateTask(task: ScheduledTask)
    suspend fun cancelTask(id: String)
    suspend fun triggerTaskNow(id: String): String
    suspend fun updateTaskStatus(
        id: String,
        lastExecutionEpoch: Long,
        status: String,
        completed: Boolean = true
    )

    /**
     * Enqueues the next run of a recurring task from its cron expression. Called after each run
     * and once at app start (see [ensureScheduled]).
     */
    suspend fun scheduleNextRun(id: String)

    /** Re-registers WorkManager jobs for enabled recurring tasks (and migrates legacy periodic jobs). */
    suspend fun ensureScheduled()

    /** Pauses (cancels pending work, keeps the definition) or resumes a task. */
    suspend fun setTaskEnabled(id: String, enabled: Boolean)

    /** Most recent executions across all tasks, newest first. */
    fun getExecutionHistory(limit: Int = 100): Flow<List<TaskExecutionRecord>>

    suspend fun recordExecution(
        taskId: String,
        taskTitle: String,
        startedAt: Long,
        finishedAt: Long,
        status: String,
        message: String,
        attempt: Int
    )
}
