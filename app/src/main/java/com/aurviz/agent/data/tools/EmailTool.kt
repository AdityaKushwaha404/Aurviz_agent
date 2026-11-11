package com.aurviz.agent.data.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aurviz.agent.core.security.SecurePreferences
import com.aurviz.agent.domain.model.SensitiveAction
import com.aurviz.agent.domain.model.ToolExecutionResponse
import com.aurviz.agent.domain.tool.AgentTool

class EmailTool(
    private val context: Context,
    private val securePreferences: SecurePreferences
) : AgentTool {

    override val name: String = "send_email"
    override val description: String = "Compose and send an email to a recipient. Requires explicit user confirmation."
    override val isSensitive: Boolean = true

    override suspend fun execute(arguments: Map<String, Any?>): ToolExecutionResponse {
        val recipient = arguments["recipient"] as? String
            ?: securePreferences.getDefaultEmail()
        val subject = arguments["subject"] as? String ?: "Automated Message from Aurviz Agent"
        val body = arguments["body"] as? String ?: ""

        val isConfirmed = arguments["isConfirmed"] as? Boolean ?: false

        if (!isConfirmed) {
            // HALT: Human-In-The-Loop Confirmation Required!
            val sensitiveAction = SensitiveAction.SendEmail(
                description = "Send email to $recipient with subject '$subject'",
                recipient = recipient,
                subject = subject,
                body = body
            )
            return ToolExecutionResponse(
                toolName = name,
                isSuccess = false,
                output = "Explicit user confirmation is required before sending this email.",
                sensitiveActionRequired = sensitiveAction
            )
        }

        // Action confirmed by user -> Execute
        return sendEmailNow(recipient, subject, body)
    }

    override suspend fun executeConfirmed(actionId: String, payload: Map<String, Any?>): ToolExecutionResponse {
        val recipient = payload["recipient"] as? String ?: securePreferences.getDefaultEmail()
        val subject = payload["subject"] as? String ?: "Automated Message"
        val body = payload["body"] as? String ?: ""
        return sendEmailNow(recipient, subject, body)
    }

    private fun sendEmailNow(recipient: String, subject: String, body: String): ToolExecutionResponse {
        return try {
            // Launch standard email client Intent
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            ToolExecutionResponse(
                toolName = name,
                isSuccess = true,
                output = "Email successfully dispatched to $recipient (Subject: '$subject'). Body length: ${body.length} chars."
            )
        } catch (e: Exception) {
            ToolExecutionResponse(
                toolName = name,
                isSuccess = false,
                output = "Failed to launch email client: ${e.localizedMessage ?: "Unknown error"}. Draft was preserved."
            )
        }
    }
}
