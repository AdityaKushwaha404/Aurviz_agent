package com.aurviz.agent.domain.model

/** A task run described for people: what happened, why, and what to do next. */
data class FriendlyLog(
    val headline: String,
    /** Plain-language body. For successful runs this is the Markdown summary the agent produced. */
    val body: String,
    /** Suggested next step, shown only when something went wrong. */
    val hint: String?,
    /** Original technical text, kept behind a "Technical details" toggle. */
    val technical: String?
)

/**
 * Turns the raw status text stored for a task run (model errors, tool failures, HTTP codes)
 * into something an end user can understand and act on.
 */
object ExecutionLogFormatter {

    fun format(state: TaskExecutionState, rawMessage: String): FriendlyLog {
        val raw = rawMessage.trim()
        return when (state) {
            TaskExecutionState.SUCCESS -> FriendlyLog(
                headline = "Completed successfully",
                body = raw.ifBlank { "The task finished without a summary." },
                hint = null,
                technical = null
            )
            TaskExecutionState.SKIPPED -> FriendlyLog(
                headline = "Skipped",
                body = raw.ifBlank { "This run was skipped." },
                hint = hintFor(raw),
                technical = null
            )
            TaskExecutionState.RETRYING -> {
                val (explanation, hint) = explain(raw)
                FriendlyLog(
                    headline = "Couldn't finish, will retry automatically",
                    body = explanation,
                    hint = hint,
                    technical = raw.takeIf { it != explanation && it.isNotBlank() }
                )
            }
            TaskExecutionState.FAILED -> {
                val (explanation, hint) = explain(raw)
                FriendlyLog(
                    headline = "The task didn't complete",
                    body = explanation,
                    hint = hint,
                    technical = raw.takeIf { it != explanation && it.isNotBlank() }
                )
            }
            TaskExecutionState.NONE -> FriendlyLog("Not run yet", "This task hasn't run yet.", null, null)
        }
    }

    /** Returns a plain explanation and an optional next step for a failure message. */
    fun explain(raw: String): Pair<String, String?> {
        val text = raw.lowercase()
        return when {
            text.isBlank() ->
                "Something went wrong, but no details were recorded." to "Try running the task again."

            "no longer available" in text || ("model" in text && "not found" in text) ->
                "The AI model used for this run is temporarily unavailable." to
                    "Aurviz switches to a backup model on the next run. Tap Run now to try again."

            "api key" in text && ("not configured" in text || "invalid" in text || "missing" in text) ->
                "The Gemini API key is missing or not accepted." to
                    "Open Settings > AI model and enter a valid key."

            "no internet" in text || "could not reach" in text || "unable to resolve" in text ||
                "timed out" in text || "timeout" in text ->
                "The phone had no working internet connection for this run." to
                    "Aurviz retries automatically when you are back online."

            "429" in text || "rate limit" in text || "quota" in text || "busy" in text ->
                "The AI service is busy right now (rate limit reached)." to
                    "Aurviz will try again shortly. You can also tap Run now later."

            "reconnect gmail" in text || "not granted" in text || "authorization expired" in text ||
                "session expired" in text || "disconnected" in text ->
                "Aurviz lost access to your Gmail account." to
                    "Open Settings > Gmail and tap Connect Gmail again."

            "gmail is not connected" in text || "connect gmail" in text ->
                "Gmail isn't connected, so the task couldn't read your email." to
                    "Open Settings > Gmail and connect your account, or turn on the demo mailbox."

            "requires explicit user approval" in text || "needs your approval" in text ->
                "This task tried to send an email. Sending always needs your approval, so nothing was sent." to
                    "Open Aurviz and run the task from the chat to review and approve it."

            "step action limit" in text || "action limit" in text ->
                "The task needed more steps than Aurviz allows in the background." to
                    "Make the instruction narrower, for example \"summarize today's unread emails\"."

            "temporarily unavailable" in text || "http 5" in text || "503" in text ->
                "A service Aurviz depends on is temporarily unavailable." to
                    "This usually clears up in a few minutes. Tap Run now to try again."

            "unexpected execution error" in text ->
                "An unexpected problem stopped the task." to
                    "Try again. If it keeps happening, edit the task instruction."

            else -> cleaned(raw) to null
        }
    }

    private fun hintFor(raw: String): String? = when {
        "gmail is not connected" in raw.lowercase() ->
            "Open Settings > Gmail and connect your account, or turn on the demo mailbox."
        else -> null
    }

    /** Strips technical prefixes so unknown errors still read naturally. */
    private fun cleaned(raw: String): String = raw
        .replace(Regex("(?i)^task failed:\\s*"), "")
        .replace(Regex("(?i)^gemini request failed:\\s*"), "")
        .replace(Regex("(?i)^gemini error:\\s*"), "")
        .replace(Regex("(?i)\\b([a-z]+_[a-z_]+) failed:"), "A step failed:")
        .take(400)
}
