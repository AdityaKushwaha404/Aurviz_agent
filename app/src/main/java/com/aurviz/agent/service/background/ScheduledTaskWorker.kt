package com.aurviz.agent.service.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aurviz.agent.AurvizApp
import com.aurviz.agent.R

class ScheduledTaskWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val promptAction = inputData.getString(KEY_PROMPT_ACTION) ?: return Result.failure()
        val taskTitle = inputData.getString(KEY_TASK_TITLE) ?: "Scheduled Agent Task"

        return try {
            val app = applicationContext as AurvizApp
            val orchestrator = app.agentOrchestrator
            val schedulerRepo = app.taskSchedulerRepository

            // Execute the automated agent workflow
            val resultSummary = orchestrator.executeAutonomousTask(promptAction)

            // Update database execution record
            schedulerRepo.updateTaskStatus(
                id = taskId,
                lastExecutionEpoch = System.currentTimeMillis(),
                status = resultSummary.take(300)
            )

            // Notify user
            showNotification(taskTitle, resultSummary)

            val scheduledTask = schedulerRepo.getTaskById(taskId)
            // Failure permanently stops periodic work. Keep recurring tasks scheduled despite one failed run.
            if (resultSummary.startsWith("Success:") || scheduledTask?.isRecurring == true) {
                Result.success()
            } else {
                Result.failure()
            }
        } catch (_: Exception) {
            val app = applicationContext as? AurvizApp
            app?.taskSchedulerRepository?.updateTaskStatus(
                id = taskId,
                lastExecutionEpoch = System.currentTimeMillis(),
                status = "Execution error; WorkManager will retry if permitted."
            )
            Result.retry()
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
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
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
        private const val CHANNEL_ID = "aurviz_agent_tasks"
    }
}
