package com.aurviz.agent.service.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aurviz.agent.AurvizApp
import com.aurviz.agent.core.util.NetworkMonitor
import com.aurviz.agent.domain.metrics.PerformanceRecorder
import com.aurviz.agent.domain.model.ScheduledTaskStatus
import com.aurviz.agent.domain.orchestrator.AutonomousTaskResult
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

/**
 * Executes one scheduled agent task. A run is only reported as successful when the
 * agent returned a final answer; failed or retrying runs are persisted as such.
 */
class ScheduledTaskWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val promptAction = inputData.getString(KEY_PROMPT_ACTION) ?: return Result.failure()
        val taskTitle = inputData.getString(KEY_TASK_TITLE) ?: "Scheduled Agent Task"

        val app = applicationContext as? AurvizApp ?: return Result.failure()
        val schedulerRepo = app.taskSchedulerRepository
        val task = schedulerRepo.getTaskById(taskId) ?: return Result.failure()

        // A paused task's periodic job may still fire once if it was already queued.
        // Manual "Run now" requests are always honoured.
        val isManual = inputData.getBoolean(KEY_MANUAL, false)
        if (!task.isEnabled && task.isRecurring && !isManual) {
            return Result.success()
        }

        // A manual trigger and a periodic run can target the same task. Ignore the
        // duplicate attempt instead of running the workflow twice.
        if (!runningTaskIds.add(taskId)) {
            return Result.success()
        }

        val startedAt = System.currentTimeMillis()
        try {
            val emailContext = EmailTaskContextFactory.create(
                promptAction = promptAction,
                gmailConnected = app.backendAuthManager.isConnected(),
                accountEmail = ""
            )
            val outcome = when {
                emailContext?.skipMessage != null ->
                    AutonomousTaskResult.Skipped(emailContext.skipMessage ?: "Email task skipped.")

                !NetworkMonitor.isOnline(applicationContext) ->
                    AutonomousTaskResult.Failure(
                        message = "No internet connection. The task will retry when a network is available.",
                        retryable = true
                    )

                else -> app.agentOrchestrator.executeAutonomousTask(
                    promptAction = promptAction,
                    additionalContext = emailContext?.additionalInstructions
                )
            }
            return persistAndScheduleNext(taskId, taskTitle, task.isRecurring, outcome, startedAt)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // Never swallow an unexpected failure: persist it and let WorkManager retry.
            return persistAndScheduleNext(
                taskId = taskId,
                taskTitle = taskTitle,
                isRecurring = task.isRecurring,
                outcome = AutonomousTaskResult.Failure(
                    message = "Unexpected execution error (${error.javaClass.simpleName}). " +
                        "Check the task instruction and try again.",
                    retryable = true
                ),
                startedAt = startedAt
            )
        } finally {
            runningTaskIds.remove(taskId)
        }
    }

    private suspend fun persistAndScheduleNext(
        taskId: String,
        taskTitle: String,
        isRecurring: Boolean,
        outcome: AutonomousTaskResult,
        startedAt: Long
    ): Result {
        val app = applicationContext as AurvizApp
        val schedulerRepo = app.taskSchedulerRepository
        val disposition = TaskExecutionPolicy.dispositionFor(outcome, isRecurring, runAttemptCount)
        val status = TaskExecutionPolicy.statusFor(outcome, disposition)
        val finishedAt = System.currentTimeMillis()

        schedulerRepo.updateTaskStatus(
            id = taskId,
            lastExecutionEpoch = finishedAt,
            status = status,
            completed = disposition != WorkDisposition.RETRY
        )
        // Every attempt (including retries) is kept in the history timeline.
        schedulerRepo.recordExecution(
            taskId = taskId,
            taskTitle = taskTitle,
            startedAt = startedAt,
            finishedAt = finishedAt,
            status = ScheduledTaskStatus.stateOf(status).name,
            message = ScheduledTaskStatus.messageOf(status),
            attempt = runAttemptCount + 1
        )
        app.performanceRecorder.record(
            PerformanceRecorder.SCHEDULED_RUN,
            finishedAt - startedAt,
            outcome is AutonomousTaskResult.Success
        )

        return when (disposition) {
            WorkDisposition.SUCCESS -> {
                showNotification(taskTitle, TaskExecutionPolicy.notificationMessage(outcome))
                Result.success()
            }
            WorkDisposition.RETRY -> Result.retry()
            WorkDisposition.FAILURE -> {
                showNotification(taskTitle, TaskExecutionPolicy.notificationMessage(outcome))
                Result.failure()
            }
        }
    }

    private fun showNotification(title: String, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aurviz Agent Background Tasks",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for recurring and scheduled agent tasks"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            (System.currentTimeMillis() % 10000).toInt(),
            openAppIntent,
            flags
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify((System.currentTimeMillis() % 100000).toInt(), notification)
    }

    companion object {
        const val KEY_TASK_ID = "key_task_id"
        const val KEY_PROMPT_ACTION = "key_prompt_action"
        const val KEY_TASK_TITLE = "key_task_title"
        const val KEY_MANUAL = "key_manual"
        private const val CHANNEL_ID = "aurviz_agent_tasks"

        private val runningTaskIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    }
}
