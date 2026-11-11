package com.aurviz.agent.service.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
                status = "Executed: ${resultSummary.take(100)}"
            )

            // Notify user
            showNotification(taskTitle, resultSummary)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            val app = applicationContext as? AurvizApp
            app?.taskSchedulerRepository?.updateTaskStatus(
                id = taskId,
                lastExecutionEpoch = System.currentTimeMillis(),
                status = "Failed: ${e.message}"
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

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        const val KEY_TASK_ID = "key_task_id"
        const val KEY_PROMPT_ACTION = "key_prompt_action"
        const val KEY_TASK_TITLE = "key_task_title"
        private const val CHANNEL_ID = "aurviz_agent_tasks"
    }
}
