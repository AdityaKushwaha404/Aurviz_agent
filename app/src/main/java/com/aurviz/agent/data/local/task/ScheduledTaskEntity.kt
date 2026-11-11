package com.aurviz.agent.data.local.task

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aurviz.agent.domain.model.ScheduledTask

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val cronExpression: String,
    val promptAction: String,
    val isRecurring: Boolean,
    val hourOfDay: Int,
    val minute: Int,
    val nextRunEpoch: Long,
    val isEnabled: Boolean,
    val lastExecutionEpoch: Long?,
    val lastExecutionStatus: String?
) {
    fun toDomain(): ScheduledTask = ScheduledTask(
        id = id,
        title = title,
        cronExpression = cronExpression,
        promptAction = promptAction,
        isRecurring = isRecurring,
        hourOfDay = hourOfDay,
        minute = minute,
        nextRunEpoch = nextRunEpoch,
        isEnabled = isEnabled,
        lastExecutionEpoch = lastExecutionEpoch,
        lastExecutionStatus = lastExecutionStatus
    )

    companion object {
        fun fromDomain(task: ScheduledTask): ScheduledTaskEntity = ScheduledTaskEntity(
            id = task.id,
            title = task.title,
            cronExpression = task.cronExpression,
            promptAction = task.promptAction,
            isRecurring = task.isRecurring,
            hourOfDay = task.hourOfDay,
            minute = task.minute,
            nextRunEpoch = task.nextRunEpoch,
            isEnabled = task.isEnabled,
            lastExecutionEpoch = task.lastExecutionEpoch,
            lastExecutionStatus = task.lastExecutionStatus
        )
    }
}
