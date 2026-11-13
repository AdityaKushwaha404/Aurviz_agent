package com.aurviz.agent.service.background

import com.aurviz.agent.domain.model.ScheduledTaskStatus
import com.aurviz.agent.domain.orchestrator.AutonomousTaskResult

/** What WorkManager should do with the current attempt. */
enum class WorkDisposition {
    /** The attempt finished and the next scheduled run can proceed normally. */
    SUCCESS,

    /** The attempt failed transiently and should be retried with backoff. */
    RETRY,

    /** The attempt failed permanently. */
    FAILURE
}

/**
 * Pure decision logic for scheduled task execution. Keeping this independent of
 * Android/WorkManager makes failure, retry and status persistence rules unit-testable.
 */
object TaskExecutionPolicy {

    /** Number of automatic retries after the first attempt. */
    const val MAX_RETRY_ATTEMPTS = 3

    fun dispositionFor(
        outcome: AutonomousTaskResult,
        isRecurring: Boolean,
        runAttemptCount: Int
    ): WorkDisposition = when (outcome) {
        is AutonomousTaskResult.Success,
        is AutonomousTaskResult.Skipped -> WorkDisposition.SUCCESS

        is AutonomousTaskResult.Failure -> {
            val retriesRemaining = runAttemptCount < MAX_RETRY_ATTEMPTS
            when {
                outcome.retryable && retriesRemaining -> WorkDisposition.RETRY
                // Failing a periodic worker permanently unregisters future runs.
                // Record the failure but keep the recurring schedule alive.
                isRecurring -> WorkDisposition.SUCCESS
                else -> WorkDisposition.FAILURE
            }
        }
    }

    /** Persisted execution log text; never reports a failed run as a success. */
    fun statusFor(outcome: AutonomousTaskResult, disposition: WorkDisposition): String =
        when (outcome) {
            is AutonomousTaskResult.Success -> ScheduledTaskStatus.success(outcome.summary)
            is AutonomousTaskResult.Skipped -> ScheduledTaskStatus.skipped(outcome.message)
            is AutonomousTaskResult.Failure -> {
                if (disposition == WorkDisposition.RETRY) {
                    ScheduledTaskStatus.retrying(outcome.message)
                } else {
                    ScheduledTaskStatus.failed(outcome.message)
                }
            }
        }

    /** Short notification text for the finished attempt. */
    fun notificationMessage(outcome: AutonomousTaskResult): String = when (outcome) {
        is AutonomousTaskResult.Success -> outcome.summary.take(300)
        is AutonomousTaskResult.Skipped -> outcome.message
        is AutonomousTaskResult.Failure -> "Task failed: ${outcome.message}"
    }
}

/** Declarative shape of the WorkManager request for a scheduled task. */
data class TaskWorkSpec(
    val uniqueWorkName: String,
    val isPeriodic: Boolean,
    val repeatIntervalHours: Long,
    val initialDelayMillis: Long,
    val requiresNetwork: Boolean,
    val backoffSeconds: Long
)

/** Builds the scheduling configuration for recurring, one-time and manual runs. */
object TaskSchedulingPolicy {

    const val BACKOFF_SECONDS = 30L

    fun forScheduledTask(
        taskId: String,
        initialDelayMillis: Long,
        isRecurring: Boolean
    ): TaskWorkSpec = TaskWorkSpec(
        uniqueWorkName = "task_$taskId",
        // Recurring tasks are not periodic jobs: each run schedules the next occurrence from the
        // task's cron expression, so weekday and day-of-week rules are possible.
        isPeriodic = false,
        repeatIntervalHours = 0L,
        initialDelayMillis = initialDelayMillis.coerceAtLeast(0L),
        requiresNetwork = true,
        backoffSeconds = BACKOFF_SECONDS
    )

    fun forManualTrigger(taskId: String): TaskWorkSpec = TaskWorkSpec(
        uniqueWorkName = "task_${taskId}_manual",
        isPeriodic = false,
        repeatIntervalHours = 0L,
        initialDelayMillis = 0L,
        requiresNetwork = true,
        backoffSeconds = BACKOFF_SECONDS
    )
}
