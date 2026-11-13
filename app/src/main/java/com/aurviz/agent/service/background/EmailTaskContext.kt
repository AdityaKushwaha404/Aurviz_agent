package com.aurviz.agent.service.background

/**
 * Context injected into autonomous email tasks. Inbox data comes only from the Gmail
 * tools, so contents are never invented; tasks are skipped with a clear status when
 * Gmail is not connected.
 */
data class EmailTaskContext(
    val additionalInstructions: String?,
    val skipMessage: String?
)

object EmailTaskContextFactory {

    private val EMAIL_TASK_PATTERN = Regex(
        "\\b(email|emails|inbox|gmail|mail)\\b",
        RegexOption.IGNORE_CASE
    )

    fun isEmailTask(promptAction: String): Boolean = EMAIL_TASK_PATTERN.containsMatchIn(promptAction)

    fun create(
        promptAction: String,
        gmailConnected: Boolean,
        accountEmail: String
    ): EmailTaskContext? {
        if (!isEmailTask(promptAction)) return null

        return if (!gmailConnected) {
            EmailTaskContext(
                additionalInstructions = null,
                skipMessage = "Gmail is not connected. Connect Gmail in Settings to run email tasks."
            )
        } else {
            EmailTaskContext(
                additionalInstructions = "Use the user's connected Gmail account" + accountEmail.takeIf(String::isNotBlank)?.let { " ($it)" }.orEmpty() + ". " +
                    "Use search_emails (Gmail query syntax, e.g. 'is:important newer_than:1d') and read_email " +
                    "to gather real inbox data before summarizing. Summarize only what the tools returned: " +
                    "sender, subject, key dates and any action required. If nothing matched, say so plainly. " +
                    "Do not invent email contents or senders. You may save replies with create_email_draft, " +
                    "but never send email without explicit user approval.",
                skipMessage = null
            )
        }
    }
}
