package com.aurviz.agent.service.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailTaskContextTest {

    @Test
    fun nonEmailTask_hasNoEmailContext() {
        assertFalse(EmailTaskContextFactory.isEmailTask("Summarize today's calendar"))
        assertNull(
            EmailTaskContextFactory.create(
                promptAction = "Summarize today's calendar",
                gmailConnected = true,
                accountEmail = "user@example.com"
            )
        )
    }

    @Test
    fun emailTaskWithoutGmail_isSkippedWithActionableMessage() {
        val context = EmailTaskContextFactory.create(
            promptAction = "Check important emails and draft necessary responses",
            gmailConnected = false,
            accountEmail = "user@example.com"
        )

        assertNotNull(context)
        assertEquals(
            "Gmail is not connected. Connect Gmail in Settings to run email tasks.",
            context?.skipMessage
        )
    }

    @Test
    fun emailTaskWithGmail_getsGuardrailsAgainstInventedInboxData() {
        val context = EmailTaskContextFactory.create(
            promptAction = "Evening Email Check & Highlights",
            gmailConnected = true,
            accountEmail = "user@example.com"
        )

        assertNotNull(context)
        assertNull(context?.skipMessage)
        val instructions = context?.additionalInstructions.orEmpty()
        assertTrue(instructions.contains("user@example.com"))
        assertTrue(instructions.contains("Do not invent", ignoreCase = true))
        assertTrue(instructions.contains("Never send email", ignoreCase = true))
    }

    @Test
    fun inboxTasks_areDetectedCaseInsensitively() {
        assertTrue(EmailTaskContextFactory.isEmailTask("Check my Gmail inbox"))
        assertTrue(EmailTaskContextFactory.isEmailTask("MAIL highlights"))
        assertFalse(EmailTaskContextFactory.isEmailTask("Browse Hacker News"))
    }
}
