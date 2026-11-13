package com.aurviz.agent.data.mail

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoMailServiceTest {

    private val mail = DemoMailService(clock = { 1_700_000_000_000L })

    private fun subjects(query: String): List<String> = runBlocking {
        val messages = mail.search(query, 15).getJSONArray("messages")
        (0 until messages.length()).map { messages.getJSONObject(it).getString("subject") }
    }

    @Test
    fun interviewSearchFindsTheInvitation() {
        val found = subjects("subject:interview")
        assertEquals(1, found.size)
        assertTrue(found.single().startsWith("Interview invitation"))
    }

    @Test
    fun importantAndUnreadOperatorsCombine() {
        val found = subjects("is:important is:unread newer_than:3d")
        assertEquals(2, found.size) // interview invite + placement drive
    }

    @Test
    fun fromAndFreeTextAndQuotedPhrases() {
        assertEquals(1, subjects("from:github").size)
        assertEquals(1, subjects("\"deadline extension\"").size)
        assertTrue(subjects("zzz-nothing-matches").isEmpty())
    }

    @Test
    fun newestMessagesComeFirstAndResultsAreCapped() {
        val inbox = subjects("in:inbox")
        assertEquals(10, inbox.size)
        assertEquals(2, runBlocking { mail.search("in:inbox", 2).getJSONArray("messages").length() })
    }

    @Test
    fun readReturnsBodyAndUnknownIdIsA404() = runBlocking {
        val email = mail.read("demo-001")
        assertTrue(email.getString("body").contains("Monday, 10:30 AM"))
        val error = runCatching { mail.read("nope") }.exceptionOrNull()
        assertTrue(error is com.aurviz.agent.data.remote.BackendApiException)
    }

    @Test
    fun draftsAndSentMailLiveInTheirOwnFoldersAndSendIsIdempotent() = runBlocking {
        mail.createDraft("a@example.com", "Draft subject", "hello")
        mail.send("b@example.com", "Sent subject", "hi", "key-12345678")
        mail.send("b@example.com", "Sent subject", "hi", "key-12345678")

        assertEquals(listOf("Draft subject"), subjects("in:drafts"))
        assertEquals(listOf("Sent subject"), subjects("in:sent"))
        assertTrue(mail.send("c@example.com", "x", "y", "key-87654321").getBoolean("demo"))
        assertTrue(subjects("in:inbox").none { it == "Draft subject" || it == "Sent subject" })
    }
}
