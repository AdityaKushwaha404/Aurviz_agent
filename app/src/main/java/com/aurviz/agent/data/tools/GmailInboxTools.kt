package com.aurviz.agent.data.tools

import com.aurviz.agent.data.remote.BackendApiException
import com.aurviz.agent.domain.mail.MailService
import com.aurviz.agent.domain.model.ToolExecutionResponse
import com.aurviz.agent.domain.tool.AgentTool
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.io.IOException

/**
 * Read-side Gmail tools. They never send mail: search and read are read-only, and
 * drafts are saved to the user's Gmail Drafts folder for later review.
 */
internal object GmailToolErrors {
    fun failure(toolName: String, error: Exception): ToolExecutionResponse {
        if (error is CancellationException) throw error
        val (message, retryable) = when {
            error is BackendApiException && error.statusCode == 401 ->
                "Gmail session expired. Reconnect Gmail in Settings." to false
            error is BackendApiException && error.statusCode == 403 ->
                (error.message ?: "Gmail inbox access was not granted. Reconnect Gmail in Settings.") to false
            error is BackendApiException && error.statusCode == 404 ->
                "That email was not found. Search again to get a current message id." to false
            error is BackendApiException && (error.statusCode == 429 || error.statusCode >= 500) ->
                "Gmail is temporarily unavailable (HTTP ${error.statusCode}). Try again shortly." to true
            error is BackendApiException ->
                (error.message ?: "Gmail request failed.") to false
            error is IOException ->
                "Could not reach Gmail. Check your internet connection and try again." to true
            else -> "Gmail request failed unexpectedly." to false
        }
        return ToolExecutionResponse(toolName, isSuccess = false, output = message, retryable = retryable)
    }
}

class GmailSearchTool(private val mail: MailService) : AgentTool {
    override val name = "search_emails"
    override val description =
        "Search the connected Gmail inbox using Gmail query syntax (e.g. 'is:important newer_than:1d', " +
            "'subject:interview'). Use in:drafts or in:sent to look at those folders. Returns sender, subject, date, snippet and message id."

    override suspend fun execute(arguments: Map<String, Any?>): ToolExecutionResponse {
        if (!mail.isConnected()) return notConnected(name)
        val query = (arguments["query"] as? String)?.trim().orEmpty().ifBlank { "in:inbox newer_than:1d" }
        val max = (arguments["maxResults"] as? Number)?.toInt() ?: 10
        return try {
            val result = mail.search(query, max)
            ToolExecutionResponse(name, isSuccess = true, output = formatSearch(result))
        } catch (error: Exception) {
            GmailToolErrors.failure(name, error)
        }
    }

    companion object {
        fun formatSearch(result: JSONObject): String {
            val messages = result.optJSONArray("messages")
            if (messages == null || messages.length() == 0) {
                return "No emails matched '${result.optString("query")}'."
            }
            return buildString {
                appendLine("Found ${messages.length()} email(s) for '${result.optString("query")}':")
                for (i in 0 until messages.length()) {
                    val m = messages.getJSONObject(i)
                    append(i + 1).append(". [id=").append(m.optString("id")).append("] ")
                    if (m.optBoolean("unread")) append("(unread) ")
                    append("From: ").append(m.optString("from"))
                    append(" | Subject: ").append(m.optString("subject"))
                    append(" | Date: ").append(m.optString("date"))
                    appendLine()
                    append("   ").appendLine(m.optString("snippet").take(240))
                }
            }.trim()
        }
    }
}

class GmailReadTool(private val mail: MailService) : AgentTool {
    override val name = "read_email"
    override val description = "Read the full text of one Gmail message by the id returned from search_emails."

    override suspend fun execute(arguments: Map<String, Any?>): ToolExecutionResponse {
        if (!mail.isConnected()) return notConnected(name)
        val id = (arguments["messageId"] as? String)?.trim().orEmpty()
        if (!id.matches(Regex("^[A-Za-z0-9_-]{6,64}$"))) {
            return ToolExecutionResponse(name, false, "A valid messageId from search_emails is required.")
        }
        return try {
            val m = mail.read(id)
            ToolExecutionResponse(
                name,
                isSuccess = true,
                output = "From: ${m.optString("from")}\nTo: ${m.optString("to")}\n" +
                    "Date: ${m.optString("date")}\nSubject: ${m.optString("subject")}\n\n${m.optString("body")}"
            )
        } catch (error: Exception) {
            GmailToolErrors.failure(name, error)
        }
    }
}

class GmailDraftTool(
    private val mail: MailService,
    private val getDefaultEmail: () -> String
) : AgentTool {
    override val name = "create_email_draft"
    override val description =
        "Save an email as a Gmail draft WITHOUT sending it. Use when the user asks to draft, prepare, " +
            "or write an email but not send it."

    override suspend fun execute(arguments: Map<String, Any?>): ToolExecutionResponse {
        if (!mail.isConnected()) return notConnected(name)
        val recipient = (arguments["recipient"] as? String ?: getDefaultEmail()).trim()
        val subject = (arguments["subject"] as? String).orEmpty().trim()
        val body = arguments["body"] as? String ?: ""
        val error = when {
            !recipient.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) -> "A valid recipient email address is required."
            subject.isBlank() -> "An email subject is required."
            subject.contains('\n') || subject.contains('\r') -> "The subject cannot contain line breaks."
            subject.length > 500 || body.length > 20_000 -> "The subject or message is too long."
            else -> null
        }
        if (error != null) return ToolExecutionResponse(name, false, error)
        return try {
            mail.createDraft(recipient, subject, body)
            ToolExecutionResponse(
                name,
                isSuccess = true,
                output = (if (mail.isDemo) "Draft saved in the demo mailbox Drafts (not sent). "
                else "Draft saved in Gmail Drafts (not sent). ") + "To: $recipient, Subject: $subject"
            )
        } catch (e: Exception) {
            GmailToolErrors.failure(name, e)
        }
    }
}

private fun notConnected(tool: String) = ToolExecutionResponse(
    tool,
    isSuccess = false,
    output = "Gmail is not connected. Ask the user to connect Gmail in Settings, or turn on the demo mailbox there."
)
