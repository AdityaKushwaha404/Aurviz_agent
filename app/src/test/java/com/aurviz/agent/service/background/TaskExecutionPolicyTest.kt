package com.aurviz.agent.service.background

import com.aurviz.agent.domain.model.ScheduledTaskStatus
import com.aurviz.agent.domain.model.TaskExecutionState
import com.aurviz.agent.domain.orchestrator.AutonomousTaskResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskExecutionPolicyTest {

    @Test
    fun oneTimeSuccess_marksWorkSuccessfulAndPersistsSuccessLog() {
        val outcome = AutonomousTaskResult.Success("Digest prepared")

        val disposition = TaskExecutionPolicy.dispositionFor(outcome, isRecurring = false, runAttemptCount = 0)
        val status = TaskExecutionPolicy.statusFor(outcome, disposition)

        assertEquals(WorkDisposition.SUCCESS, disposition)
        assertTrue(status.startsWith(ScheduledTaskStatus.PREFIX_SUCCESS))
        assertEquals(TaskExecutionState.SUCCESS, ScheduledTaskStatus.stateOf(status))
    }

    @Test
    fun periodicSuccess_keepsPeriodicScheduleConfigured() {
        val outcome = AutonomousTaskResult.Success("Evening summary ready")

        val disposition = TaskExecutionPolicy.dispositionFor(outcome, isRecurring = true, runAttemptCount = 0)
        val spec = TaskSchedulingPolicy.forScheduledTask("task-1", 60_000L, isRecurring = true)

        assertEquals(WorkDisposition.SUCCESS, disposition)
        assertTrue(spec.isPeriodic)
        assertEquals(24L, spec.repeatIntervalHours)
        assertEquals("task_task-1", spec.uniqueWorkName)
        assertTrue(spec.requiresNetwork)
    }

    @Test
    fun oneTimeScheduling_buildsDelayedNonPeriodicWork() {
        val spec = TaskSchedulingPolicy.forScheduledTask("task-2", 90_000L, isRecurring = false)

        assertFalse(spec.isPeriodic)
        assertEquals(90_000L, spec.initialDelayMillis)
        assertEquals(TaskSchedulingPolicy.BACKOFF_SECONDS, spec.backoffSeconds)
    }

    @Test
    fun failedRun_isPersistedAsFailureNotSuccess() {
        val outcome = AutonomousTaskResult.Failure("Gemini model is no longer available (HTTP 404).")

        val disposition = TaskExecutionPolicy.dispositionFor(outcome, isRecurring = false, runAttemptCount = 3)
        val status = TaskExecutionPolicy.statusFor(outcome, disposition)

        assertEquals(WorkDisposition.FAILURE, disposition)
        assertTrue(status.startsWith(ScheduledTaskStatus.PREFIX_FAILED))
        assertEquals(TaskExecutionState.FAILED, ScheduledTaskStatus.stateOf(status))
        assertFalse(ScheduledTaskStatus.isFailed(ScheduledTaskStatus.success("ok")))
    }

    @Test
    fun retryableFailure_isRetriedWithRetryLog() {
        val outcome = AutonomousTaskResult.Failure("Gemini rate limit exceeded (HTTP 429).", retryable = true)

        val disposition = TaskExecutionPolicy.dispositionFor(outcome, isRecurring = false, runAttemptCount = 0)
        val status = TaskExecutionPolicy.statusFor(outcome, disposition)

        assertEquals(WorkDisposition.RETRY, disposition)
        assertEquals(TaskExecutionState.RETRYING, ScheduledTaskStatus.stateOf(status))
    }

    @Test
    fun retryableFailure_stopsAfterMaxAttemptsForOneTimeTask() {
        val outcome = AutonomousTaskResult.Failure("No internet connection.", retryable = true)

        val disposition = TaskExecutionPolicy.dispositionFor(
            outcome,
            isRecurring = false,
            runAttemptCount = TaskExecutionPolicy.MAX_RETRY_ATTEMPTS
        )

        assertEquals(WorkDisposition.FAILURE, disposition)
        assertEquals(
            TaskExecutionState.FAILED,
            ScheduledTaskStatus.stateOf(TaskExecutionPolicy.statusFor(outcome, disposition))
        )
    }

    @Test
    fun exhaustedPeriodicFailure_keepsFutureRunsWhileLoggingFailure() {
        val outcome = AutonomousTaskResult.Failure("Gemini service error (HTTP 500).", retryable = true)

        val disposition = TaskExecutionPolicy.dispositionFor(
            outcome,
            isRecurring = true,
            runAttemptCount = TaskExecutionPolicy.MAX_RETRY_ATTEMPTS
        )
        val status = TaskExecutionPolicy.statusFor(outcome, disposition)

        // Result.success() keeps periodic work registered, but the log must not claim success.
        assertEquals(WorkDisposition.SUCCESS, disposition)
        assertEquals(TaskExecutionState.FAILED, ScheduledTaskStatus.stateOf(status))
    }

    @Test
    fun nonRetryablePeriodicFailure_keepsScheduleAndFailedLog() {
        val outcome = AutonomousTaskResult.Failure("Gmail is not connected.")

        val disposition = TaskExecutionPolicy.dispositionFor(outcome, isRecurring = true, runAttemptCount = 0)

        assertEquals(WorkDisposition.SUCCESS, disposition)
        assertEquals(
            TaskExecutionState.FAILED,
            ScheduledTaskStatus.stateOf(TaskExecutionPolicy.statusFor(outcome, disposition))
        )
    }

    @Test
    fun skippedOutcome_isNotReportedAsFailure() {
        val outcome = AutonomousTaskResult.Skipped("Inbox is empty; nothing to summarize.")

        val disposition = TaskExecutionPolicy.dispositionFor(outcome, isRecurring = true, runAttemptCount = 0)
        val status = TaskExecutionPolicy.statusFor(outcome, disposition)

        assertEquals(WorkDisposition.SUCCESS, disposition)
        assertEquals(TaskExecutionState.SKIPPED, ScheduledTaskStatus.stateOf(status))
    }

    @Test
    fun manualTrigger_usesUniqueManualWorkName() {
        val spec = TaskSchedulingPolicy.forManualTrigger("task-3")

        assertFalse(spec.isPeriodic)
        assertEquals("task_task-3_manual", spec.uniqueWorkName)
        assertEquals(0L, spec.initialDelayMillis)
        assertTrue(spec.requiresNetwork)
    }

    @Test
    fun legacyFailedStatus_isRecognisedAndUnprefixedForDisplay() {
        val legacy = "Task failed: Gemini Error: model retired"

        assertEquals(TaskExecutionState.FAILED, ScheduledTaskStatus.stateOf(legacy))
        assertTrue(ScheduledTaskStatus.isFailed(legacy))
        assertEquals(
            "Gemini model unavailable",
            ScheduledTaskStatus.messageOf(ScheduledTaskStatus.failed("Gemini model unavailable"))
        )
    }
}
