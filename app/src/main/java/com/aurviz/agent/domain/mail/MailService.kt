package com.aurviz.agent.domain.mail

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Mailbox operations used by the Gmail agent tools. Real accounts are served by the
 * Aurviz backend; the demo implementation serves a built-in sample mailbox so the app
 * can be evaluated without any Google account or credentials.
 */
interface MailService {
    val isDemo: Boolean get() = false
    fun isConnected(): Boolean
    suspend fun search(query: String, maxResults: Int): JSONObject
    suspend fun read(messageId: String): JSONObject
    suspend fun createDraft(recipient: String, subject: String, body: String): JSONObject
    suspend fun send(recipient: String, subject: String, body: String, idempotencyKey: String): JSONObject
}

/** Chooses the demo mailbox or the real account at call time, so the toggle applies instantly. */
class MailGateway(
    private val real: MailService,
    private val demo: com.aurviz.agent.data.mail.DemoMailService,
    private val demoEnabled: () -> Boolean
) : MailService {
    private val _revision = MutableStateFlow(0L)

    /** Increments whenever the mailbox changes (a draft saved, a message sent, demo reset) so views can refresh. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun resetDemo() {
        demo.reset()
        _revision.value++
    }

    private val active: MailService get() = if (demoEnabled()) demo else real
    override val isDemo: Boolean get() = demoEnabled()
    override fun isConnected() = active.isConnected()
    override suspend fun search(query: String, maxResults: Int) = active.search(query, maxResults)
    override suspend fun read(messageId: String) = active.read(messageId)
    override suspend fun createDraft(recipient: String, subject: String, body: String) =
        active.createDraft(recipient, subject, body).also { _revision.value++ }
    override suspend fun send(recipient: String, subject: String, body: String, idempotencyKey: String) =
        active.send(recipient, subject, body, idempotencyKey).also { _revision.value++ }
}
