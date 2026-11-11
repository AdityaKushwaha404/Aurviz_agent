package com.aurviz.agent.data.repository

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.aurviz.agent.core.util.TimeUtils
import com.aurviz.agent.data.local.task.ScheduledTaskDao
import com.aurviz.agent.data.local.task.ScheduledTaskEntity
import com.aurviz.agent.domain.model.ScheduledTask
import com.aurviz.agent.domain.repository.TaskSchedulerRepository
import com.aurviz.agent.service.background.ScheduledTaskWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

class TaskSchedulerRepositoryImpl(
    private val context: Context,
    private val taskDao: ScheduledTaskDao
) : TaskSchedulerRepository {

    private val workManager = WorkManager.getInstance(context)

    override fun getAllTasks(): Flow<List<ScheduledTask>> {
        return taskDao.getAllTasks().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getTaskById(id: String): ScheduledTask? {
        return taskDao.getTaskById(id)?.toDomain()
    }

    override suspend fun scheduleTask(task: ScheduledTask) {
        val initialDelay = TimeUtils.calculateInitialDelayForTime(task.hourOfDay, task.minute)
        val nextRunEpoch = System.currentTimeMillis() + initialDelay

        val updatedTask = task.copy(nextRunEpoch = nextRunEpoch, isEnabled = true)
        taskDao.insertTask(ScheduledTaskEntity.fromDomain(updatedTask))

        val inputData = workDataOf(
            ScheduledTaskWorker.KEY_TASK_ID to task.id,
            ScheduledTaskWorker.KEY_PROMPT_ACTION to task.promptAction,
            ScheduledTaskWorker.KEY_TASK_TITLE to task.title
        )

        if (task.isRecurring) {
            // Periodic WorkRequest runs once every 24 hours with calculated initial delay
            val workRequest = PeriodicWorkRequestBuilder<ScheduledTaskWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag("task_${task.id}")
                .build()

            workManager.enqueueUniquePeriodicWork(
                "task_${task.id}",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        } else {
            // One-time delayed WorkRequest
            val workRequest = OneTimeWorkRequestBuilder<ScheduledTaskWorker>()
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag("task_${task.id}")
                .build()

            workManager.enqueueUniqueWork(
                "task_${task.id}",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    override suspend fun cancelTask(id: String) {
        workManager.cancelUniqueWork("task_$id")
        taskDao.deleteTask(id)
    }

    override suspend fun triggerTaskNow(id: String): String {
        val task = taskDao.getTaskById(id) ?: return "Task not found"
        val inputData = workDataOf(
            ScheduledTaskWorker.KEY_TASK_ID to task.id,
            ScheduledTaskWorker.KEY_PROMPT_ACTION to task.promptAction,
            ScheduledTaskWorker.KEY_TASK_TITLE to task.title
        )

        val workRequest = OneTimeWorkRequestBuilder<ScheduledTaskWorker>()
            .setInputData(inputData)
            .build()

        workManager.enqueue(workRequest)
        return "Task '${task.title}' triggered immediately in background."
    }

    override suspend fun updateTaskStatus(id: String, lastExecutionEpoch: Long, status: String) {
        taskDao.updateExecutionStatus(id, lastExecutionEpoch, status)
    }
}
