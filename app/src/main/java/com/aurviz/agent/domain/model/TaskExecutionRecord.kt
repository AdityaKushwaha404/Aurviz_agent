package com.aurviz.agent.domain.model

/** A historical execution of a scheduled task, shown in the task history timeline. */
data class TaskExecutionRecord(
    val id: Long,
    val taskId: String,
    val taskTitle: String,
    val startedAt: Long,
    val finishedAt: Long,
    /** One of the [TaskExecutionState] names. */
    val status: String,
    val message: String,
    val attempt: Int
) {
    val durationMs: Long get() = (finishedAt - startedAt).coerceAtLeast(0)
    val state: TaskExecutionState
        get() = runCatching { TaskExecutionState.valueOf(status) }.getOrDefault(TaskExecutionState.NONE)
}
