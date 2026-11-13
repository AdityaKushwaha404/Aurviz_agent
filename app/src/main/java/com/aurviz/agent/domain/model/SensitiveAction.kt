package com.aurviz.agent.domain.model

sealed class SensitiveAction {
    abstract val actionId: String
    abstract val description: String

    data class SendEmail(
        override val actionId: String = java.util.UUID.randomUUID().toString(),
        override val description: String,
        val recipient: String,
        val subject: String,
        val body: String
    ) : SensitiveAction()

    data class BrowserInteraction(
        override val actionId: String = java.util.UUID.randomUUID().toString(),
        override val description: String,
        val operation: String,
        val selector: String,
        val text: String? = null,
        val pageTitle: String,
        val pageUrl: String
    ) : SensitiveAction()
}
