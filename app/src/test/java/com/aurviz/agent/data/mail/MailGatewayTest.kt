package com.aurviz.agent.data.mail

import com.aurviz.agent.domain.mail.MailGateway
import com.aurviz.agent.domain.mail.MailService
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailGatewayTest {

    private class MemoryStore : DemoMailStore {
        var saved: List<DemoEmail> = emptyList()
        override fun load() = saved
        override fun save(emails: List<DemoEmail>) { saved = emails }
    }

    private class FakeReal : MailService {
        var searches = 0
        override fun isConnected() = true
        override suspend fun search(query: String, maxResults: Int): JSONObject { searches++; return JSONObject() }
        override suspend fun read(messageId: String) = JSONObject()
        override suspend fun createDraft(recipient: String, subject: String, body: String) = JSONObject()
        override suspend fun send(recipient: String, subject: String, body: String, idempotencyKey: String) = JSONObject()
    }

    @Test
    fun demoDraftsAndSentMailSurviveARestartAndResetRestoresTheSample() = runBlocking {
        val store = MemoryStore()
        val first = DemoMailService(store = store)
        first.createDraft("a@example.com", "Draft one", "hello")
        first.send("b@example.com", "Sent one", "hi", "key-abcdefgh")

        val restarted = DemoMailService(store = store)
        assertEquals(1, restarted.search("in:drafts", 15).getJSONArray("messages").length())
        assertEquals(1, restarted.search("in:sent", 15).getJSONArray("messages").length())
        assertEquals(10, restarted.search("in:inbox", 15).getJSONArray("messages").length())

        restarted.reset()
        assertEquals(0, restarted.search("in:drafts", 15).getJSONArray("messages").length())
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun gatewayRoutesBySourceAndBumpsRevisionOnChanges() = runBlocking {
        var demoOn = true
        val real = FakeReal()
        val gateway = MailGateway(real, DemoMailService(), demoEnabled = { demoOn })

        assertTrue(gateway.isDemo)
        gateway.search("in:inbox", 5)
        assertEquals(0, real.searches)

        val before = gateway.revision.value
        gateway.createDraft("a@example.com", "s", "b")
        gateway.send("a@example.com", "s", "b", "key-12345678")
        assertEquals(before + 2, gateway.revision.value)

        gateway.resetDemo()
        assertEquals(before + 3, gateway.revision.value)

        demoOn = false
        assertFalse(gateway.isDemo)
        gateway.search("in:inbox", 5)
        assertEquals(1, real.searches)
    }
}
