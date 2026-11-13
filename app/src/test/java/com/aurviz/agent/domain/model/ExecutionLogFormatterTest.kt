package com.aurviz.agent.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionLogFormatterTest {

    @Test
    fun retiredModelErrorBecomesPlainLanguageWithNextStep() {
        val log = ExecutionLogFormatter.format(
            TaskExecutionState.FAILED,
            "Task failed: Gemini Error: This model models/gemini-2.0-flash-lite is no longer available."
        )
        assertEquals("The task didn't complete", log.headline)
        assertTrue(log.body.contains("temporarily unavailable"))
        assertNotNull(log.hint)
        assertTrue(log.technical!!.contains("gemini-2.0-flash-lite"))
    }

    @Test
    fun offlineAndRateLimitAreExplainedDifferently() {
        val offline = ExecutionLogFormatter.explain("No internet connection. The task will retry when a network is available.")
        val busy = ExecutionLogFormatter.explain("Gemini request failed: HTTP 429 quota exceeded")
        assertTrue(offline.first.contains("internet"))
        assertTrue(busy.first.contains("busy"))
    }

    @Test
    fun gmailAccessProblemsPointToSettings() {
        val (explanation, hint) = ExecutionLogFormatter.explain("search_emails failed: Gmail inbox access was not granted. Reconnect Gmail")
        assertTrue(explanation.contains("Gmail"))
        assertTrue(hint!!.contains("Settings"))
    }

    @Test
    fun blockedSendExplainsNothingWasSent() {
        val (explanation, _) = ExecutionLogFormatter.explain("The task needs your approval to run 'send_email'. Nothing was executed.")
        assertTrue(explanation.contains("nothing was sent", ignoreCase = true))
    }

    @Test
    fun successKeepsSummaryAndHasNoHint() {
        val log = ExecutionLogFormatter.format(TaskExecutionState.SUCCESS, "**3 emails** found.")
        assertEquals("Completed successfully", log.headline)
        assertEquals("**3 emails** found.", log.body)
        assertNull(log.hint)
        assertNull(log.technical)
    }

    @Test
    fun unknownErrorsAreCleanedNotHidden() {
        val log = ExecutionLogFormatter.format(TaskExecutionState.FAILED, "Task failed: weird thing happened")
        assertEquals("weird thing happened", log.body)
        assertFalse(log.body.startsWith("Task failed"))
    }
}
