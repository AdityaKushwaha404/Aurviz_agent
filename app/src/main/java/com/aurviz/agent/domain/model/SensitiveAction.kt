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

    data class DeleteData(
        override val actionId: String = java.util.UUID.randomUUID().toString(),
        override val description: String,
        val target: String
    ) : SensitiveAction()
}
