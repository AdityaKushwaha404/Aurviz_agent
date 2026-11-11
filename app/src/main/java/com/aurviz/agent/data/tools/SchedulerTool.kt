package com.aurviz.agent.data.tools

import com.aurviz.agent.core.util.TimeUtils
import com.aurviz.agent.domain.model.ScheduledTask
import com.aurviz.agent.domain.model.ToolExecutionResponse
import com.aurviz.agent.domain.repository.TaskSchedulerRepository
import com.aurviz.agent.domain.tool.AgentTool
import java.util.UUID

class SchedulerTool(
    private val taskSchedulerRepository: TaskSchedulerRepository
) : AgentTool {

    override val name: String = "schedule_task"
    override val description: String = "Schedule a recurring or delayed task (e.g. 'Every morning at 9 AM, check emails'). Requires cron or time expression and action prompt."

    override suspend fun execute(arguments: Map<String, Any?>): ToolExecutionResponse {
        val timeExpression = arguments["timeExpression"] as? String ?: "9:00 AM"
        val promptAction = arguments["promptAction"] as? String
            ?: return ToolExecutionResponse(name, false, "Missing 'promptAction' parameter.")
        val title = arguments["title"] as? String ?: promptAction.take(30)
        val isRecurring = (arguments["isRecurring"] as? Boolean) ?: true

        val (hour, minute) = TimeUtils.parseHourAndMinute(timeExpression)

        val task = ScheduledTask(
            id = UUID.randomUUID().toString(),
            title = title,
            cronExpression = "daily_at_${String.format("%02d:%02d", hour, minute)}",
            promptAction = promptAction,
            isRecurring = isRecurring,
            hourOfDay = hour,
            minute = minute
        )

        taskSchedulerRepository.scheduleTask(task)

        return ToolExecutionResponse(
            toolName = name,
            isSuccess = true,
            output = "Scheduled task successfully registered: '$title' to execute ${if (isRecurring) "daily" else "once"} at ${String.format("%02d:%02d", hour, minute)}."
        )
    }
}
