package com.aurviz.agent.domain.mail

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
    private val demo: MailService,
    private val demoEnabled: () -> Boolean
) : MailService {
    private val active: MailService get() = if (demoEnabled()) demo else real
    override val isDemo: Boolean get() = demoEnabled()
    override fun isConnected() = active.isConnected()
    override suspend fun search(query: String, maxResults: Int) = active.search(query, maxResults)
    override suspend fun read(messageId: String) = active.read(messageId)
    override suspend fun createDraft(recipient: String, subject: String, body: String) =
        active.createDraft(recipient, subject, body)
    override suspend fun send(recipient: String, subject: String, body: String, idempotencyKey: String) =
        active.send(recipient, subject, body, idempotencyKey)
}
