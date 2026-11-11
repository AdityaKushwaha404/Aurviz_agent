package com.aurviz.agent.domain.model

data class ScheduledTask(
    val id: String, // UUID
    val title: String,
    val cronExpression: String, // e.g. "0 9 * * *" or "daily_at_09:00"
    val promptAction: String, // e.g. "Check my important emails and prepare a summary"
    val isRecurring: Boolean = true,
    val hourOfDay: Int = 9,
    val minute: Int = 0,
    val nextRunEpoch: Long = 0L,
    val isEnabled: Boolean = true,
    val lastExecutionEpoch: Long? = null,
    val lastExecutionStatus: String? = null
)
