package com.aurviz.agent.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.aurviz.agent.core.util.TimeUtils
import com.aurviz.agent.data.local.history.TaskExecutionDao
import com.aurviz.agent.data.local.history.TaskExecutionEntity
import com.aurviz.agent.data.local.task.ScheduledTaskDao
import com.aurviz.agent.data.local.task.ScheduledTaskEntity
import com.aurviz.agent.domain.model.ScheduledTask
import com.aurviz.agent.domain.model.TaskExecutionRecord
import com.aurviz.agent.domain.repository.TaskSchedulerRepository
import com.aurviz.agent.service.background.ScheduledTaskWorker
import com.aurviz.agent.service.background.TaskSchedulingPolicy
import com.aurviz.agent.service.background.TaskWorkSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

class TaskSchedulerRepositoryImpl(
    private val context: Context,
    private val taskDao: ScheduledTaskDao,
    private val executionDao: TaskExecutionDao
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

        val spec = TaskSchedulingPolicy.forScheduledTask(
            taskId = task.id,
            initialDelayMillis = initialDelay,
            isRecurring = task.isRecurring
        )
        val inputData = inputDataFor(task)

        if (spec.isPeriodic) {
            val workRequest = PeriodicWorkRequestBuilder<ScheduledTaskWorker>(
                spec.repeatIntervalHours,
                TimeUnit.HOURS
            )
                .setInitialDelay(spec.initialDelayMillis, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .setConstraints(constraintsFor(spec))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, spec.backoffSeconds, TimeUnit.SECONDS)
                .addTag("task_${task.id}")
                .build()

            workManager.enqueueUniquePeriodicWork(
                spec.uniqueWorkName,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        } else {
            val workRequest = OneTimeWorkRequestBuilder<ScheduledTaskWorker>()
                .setInitialDelay(spec.initialDelayMillis, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .setConstraints(constraintsFor(spec))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, spec.backoffSeconds, TimeUnit.SECONDS)
                .addTag("task_${task.id}")
                .build()

            workManager.enqueueUniqueWork(
                spec.uniqueWorkName,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    override suspend fun updateTask(task: ScheduledTask) {
        workManager.cancelUniqueWork(TaskSchedulingPolicy.forScheduledTask(task.id, 0L, task.isRecurring).uniqueWorkName)
        workManager.cancelUniqueWork(TaskSchedulingPolicy.forManualTrigger(task.id).uniqueWorkName)
        scheduleTask(task)
    }

    override suspend fun cancelTask(id: String) {
        workManager.cancelUniqueWork(TaskSchedulingPolicy.forScheduledTask(id, 0L, isRecurring = true).uniqueWorkName)
        workManager.cancelUniqueWork(TaskSchedulingPolicy.forManualTrigger(id).uniqueWorkName)
        taskDao.deleteTask(id)
        executionDao.deleteForTask(id)
    }

    override suspend fun setTaskEnabled(id: String, enabled: Boolean) {
        val task = taskDao.getTaskById(id)?.toDomain() ?: return
        if (enabled) {
            // Re-registers the WorkManager job and recomputes the next run time.
            scheduleTask(task)
        } else {
            cancelWork(task.id, task.isRecurring)
            taskDao.updateTaskEnabled(id, false)
        }
    }

    override fun getExecutionHistory(limit: Int): Flow<List<TaskExecutionRecord>> =
        executionDao.recent(limit).map { rows -> rows.map { it.toDomain() } }

    override suspend fun recordExecution(
        taskId: String,
        taskTitle: String,
        startedAt: Long,
        finishedAt: Long,
        status: String,
        message: String,
        attempt: Int
    ) {
        executionDao.insert(
            TaskExecutionEntity(
                taskId = taskId,
                taskTitle = taskTitle,
                startedAt = startedAt,
                finishedAt = finishedAt,
                status = status,
                message = message.take(2000),
                attempt = attempt
            )
        )
        executionDao.trim(MAX_HISTORY_ROWS)
    }

    private fun cancelWork(taskId: String, isRecurring: Boolean) {
        workManager.cancelUniqueWork(TaskSchedulingPolicy.forScheduledTask(taskId, 0L, isRecurring).uniqueWorkName)
        workManager.cancelUniqueWork(TaskSchedulingPolicy.forManualTrigger(taskId).uniqueWorkName)
    }

    override suspend fun triggerTaskNow(id: String): String {
        val task = taskDao.getTaskById(id) ?: return "Task not found"
        val spec = TaskSchedulingPolicy.forManualTrigger(task.id)

        val workRequest = OneTimeWorkRequestBuilder<ScheduledTaskWorker>()
            .setInputData(
                workDataOf(
                    ScheduledTaskWorker.KEY_TASK_ID to task.id,
                    ScheduledTaskWorker.KEY_PROMPT_ACTION to task.promptAction,
                    ScheduledTaskWorker.KEY_TASK_TITLE to task.title,
                    ScheduledTaskWorker.KEY_MANUAL to true
                )
            )
            .setConstraints(constraintsFor(spec))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, spec.backoffSeconds, TimeUnit.SECONDS)
            .addTag("task_${task.id}")
            .build()

        // KEEP prevents tapping "Trigger Now"/"Retry" repeatedly from queuing duplicate runs.
        workManager.enqueueUniqueWork(spec.uniqueWorkName, ExistingWorkPolicy.KEEP, workRequest)
        return "Task '${task.title}' queued. Android may defer background work while the device is idle."
    }

    override suspend fun updateTaskStatus(
        id: String,
        lastExecutionEpoch: Long,
        status: String,
        completed: Boolean
    ) {
        val task = taskDao.getTaskById(id) ?: return
        val nextRunEpoch = if (task.isRecurring) {
            lastExecutionEpoch + TimeUnit.DAYS.toMillis(1)
        } else {
            task.nextRunEpoch
        }
        // Recurring work stays registered. A one-time task stays enabled while a retry
        // is pending, and is marked completed only after a final attempt.
        // A recurring task keeps its paused/active state; a run never silently resumes it.
        val enabled = if (task.isRecurring) task.isEnabled else !completed
        taskDao.updateExecutionStatus(id, lastExecutionEpoch, status, nextRunEpoch, enabled)
    }

    private fun inputDataFor(task: ScheduledTask) =
        inputDataFor(task.id, task.promptAction, task.title)

    private fun inputDataFor(taskId: String, promptAction: String, title: String) = workDataOf(
        ScheduledTaskWorker.KEY_TASK_ID to taskId,
        ScheduledTaskWorker.KEY_PROMPT_ACTION to promptAction,
        ScheduledTaskWorker.KEY_TASK_TITLE to title
    )

    private fun constraintsFor(spec: TaskWorkSpec): Constraints = Constraints.Builder()
        .setRequiredNetworkType(
            if (spec.requiresNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED
        )
        .build()

    private companion object {
        const val MAX_HISTORY_ROWS = 500
    }
}
