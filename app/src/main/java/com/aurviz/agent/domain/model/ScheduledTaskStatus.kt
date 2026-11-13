package com.aurviz.agent.domain.model

/** Execution state of a scheduled task, derived from its persisted status text. */
enum class TaskExecutionState {
    NONE,
    SUCCESS,
    FAILED,
    RETRYING,
    SKIPPED
}

/**
 * Canonical prefixes for persisted scheduled-task execution logs. The status text is
 * stored in Room, so these prefixes let the UI distinguish a successful run from a
 * failed or pending-retry run without a schema change.
 */
object ScheduledTaskStatus {

    const val PREFIX_SUCCESS = "Success:"
    const val PREFIX_FAILED = "Failed:"
    const val PREFIX_RETRYING = "Retrying:"
    const val PREFIX_SKIPPED = "Skipped:"

    fun success(message: String): String = format(PREFIX_SUCCESS, message)
    fun failed(message: String): String = format(PREFIX_FAILED, message)
    fun retrying(message: String): String = format(PREFIX_RETRYING, message)
    fun skipped(message: String): String = format(PREFIX_SKIPPED, message)

    fun stateOf(status: String?): TaskExecutionState {
        val value = status?.trim().orEmpty()
        return when {
            value.isEmpty() -> TaskExecutionState.NONE
            value.startsWith(PREFIX_SUCCESS, ignoreCase = true) -> TaskExecutionState.SUCCESS
            value.startsWith(PREFIX_FAILED, ignoreCase = true) -> TaskExecutionState.FAILED
            value.startsWith(PREFIX_RETRYING, ignoreCase = true) -> TaskExecutionState.RETRYING
            value.startsWith(PREFIX_SKIPPED, ignoreCase = true) -> TaskExecutionState.SKIPPED
            // Legacy rows written before prefixes were introduced.
            value.startsWith("Task failed", ignoreCase = true) -> TaskExecutionState.FAILED
            else -> TaskExecutionState.SUCCESS
        }
    }

    fun isFailed(status: String?): Boolean = stateOf(status) == TaskExecutionState.FAILED

    /** Status text without its machine-readable prefix, for display. */
    fun messageOf(status: String?): String {
        val value = status?.trim().orEmpty()
        val prefix = listOf(PREFIX_SUCCESS, PREFIX_FAILED, PREFIX_RETRYING, PREFIX_SKIPPED)
            .firstOrNull { value.startsWith(it, ignoreCase = true) }
        return prefix?.let { value.substring(it.length).trim() } ?: value
    }

    private fun format(prefix: String, message: String): String =
        "$prefix ${message.trim()}".take(MAX_STATUS_CHARS)

    private const val MAX_STATUS_CHARS = 500
}
