package com.aurviz.agent.data.tools

import com.aurviz.agent.domain.mail.MailService
import com.aurviz.agent.domain.model.SensitiveAction
import com.aurviz.agent.domain.model.ToolExecutionResponse
import com.aurviz.agent.domain.tool.AgentTool
import java.io.IOException
import java.util.UUID

/** Sends mail through the Aurviz backend only after the agent confirmation flow approves it. */
class EmailTool(
    private val mail: MailService,
    private val getDefaultEmail: () -> String
) : AgentTool {
    override val name: String = "send_email"
    override val description: String =
        "Send an email through the connected Gmail account after the user explicitly confirms the recipient, subject, and body."
    override val isSensitive: Boolean = true

    override suspend fun execute(arguments: Map<String, Any?>): ToolExecutionResponse {
        val recipient = (arguments["recipient"] as? String ?: getDefaultEmail()).trim()
        val subject = (arguments["subject"] as? String ?: "").trim()
        val body = arguments["body"] as? String ?: ""
        val validationError = validate(recipient, subject, body)
        if (validationError != null) return failure(validationError)
        if (!mail.isConnected()) {
            return failure("Choose a mailbox in Settings, Mailbox before sending email.")
        }
        return ToolExecutionResponse(
            toolName = name,
            isSuccess = false,
            output = "Review the recipient, subject, and message, then confirm to send.",
            sensitiveActionRequired = SensitiveAction.SendEmail(
                description = "Send an email to " + recipient + " with subject '" + subject + "'",
                recipient = recipient,
                subject = subject,
                body = body
            )
        )
    }

    override suspend fun executeConfirmed(
        actionId: String,
        payload: Map<String, Any?>
    ): ToolExecutionResponse {
        val recipient = (payload["recipient"] as? String ?: getDefaultEmail()).trim()
        val subject = payload["subject"] as? String ?: ""
        val body = payload["body"] as? String ?: ""
        validate(recipient, subject, body)?.let { return failure(it) }
        if (!mail.isConnected()) {
            return failure("Choose a mailbox in Settings, Mailbox before sending email.")
        }

        return try {
            val requestKey = actionId.takeIf { it.matches(Regex("^[A-Za-z0-9._:-]{8,120}$")) }
                ?: UUID.randomUUID().toString()
            val result = mail.send(recipient, subject, body, requestKey)
            ToolExecutionResponse(
                toolName = name,
                isSuccess = true,
                output = if (result.optBoolean("demo")) {
                    "Demo mode: the email was recorded in the demo outbox. Nothing was sent to a real address."
                } else {
                    "Email queued for sending. Gmail confirms delivery shortly after."
                }
            )
        } catch (error: Exception) {
            val message = when (error) {
                is com.aurviz.agent.data.remote.BackendApiException ->
                    if (error.statusCode in 400..499) (error.message ?: "The email was rejected.")
                    else "The mail service is temporarily unavailable. Try again shortly."
                is IOException -> error.message ?: "Could not reach Aurviz. Check your connection and try again."
                else -> "Aurviz could not accept this email. Check your connection and Gmail authorization, then try again."
            }
            failure(message)
        }
    }

    private fun validate(recipient: String, subject: String, body: String): String? = when {
        !recipient.isValidEmail() -> "A valid recipient email address is required."
        subject.isBlank() -> "An email subject is required."
        subject.contains('\r') || subject.contains('\n') -> "The email subject cannot contain line breaks."
        subject.length > MAX_SUBJECT_CHARS || body.length > MAX_BODY_CHARS ->
            "The subject or message is too long. Shorten it and try again."
        else -> null
    }

    private fun String.isValidEmail(): Boolean =
        length <= 254 && matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))

    private fun failure(message: String) = ToolExecutionResponse(
        toolName = name,
        isSuccess = false,
        output = message
    )

    private companion object {
        const val MAX_SUBJECT_CHARS = 500
        const val MAX_BODY_CHARS = 20_000
    }
}
