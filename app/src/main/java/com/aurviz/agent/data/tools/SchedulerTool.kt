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
    override val description: String = "Schedule an autonomous recurring or delayed background task. " +
            "You MUST intelligently formulate: " +
            "1) 'title': A clean, professional, capitalized heading summarizing the task (e.g. 'Daily Morning Email Digest', 'Evening Project Summary', 'Hacker News Tech Briefing'). " +
            "2) 'promptAction': A comprehensive, autonomous instruction describing exactly what the agent should do when triggered (e.g. 'Check unread emails, extract key highlights and pending action items, and compile an email summary'). " +
            "3) 'timeExpression': Time string (e.g. '9:00 PM', '21:00', '9:00 AM'). " +
            "4) 'isRecurring': boolean (true for recurring daily, false for one-time)."

    override suspend fun execute(arguments: Map<String, Any?>): ToolExecutionResponse {
        val timeExpression = arguments["timeExpression"] as? String ?: "9:00 AM"
        val rawPromptAction = arguments["promptAction"] as? String
            ?: return ToolExecutionResponse(name, false, "Missing 'promptAction' parameter.")
        val rawTitle = arguments["title"] as? String ?: ""
        val isRecurring = (arguments["isRecurring"] as? Boolean) ?: true

        val (hour, minute) = TimeUtils.tryParseHourAndMinute(timeExpression)
            ?: return ToolExecutionResponse(name, false, "Please provide a valid time, such as 9:00 AM or 21:00.")

        val cleanTitle = synthesizeTitle(rawTitle, rawPromptAction, hour)
        val cleanAction = synthesizeAction(rawPromptAction)

        val task = ScheduledTask(
            id = UUID.randomUUID().toString(),
            title = cleanTitle,
            cronExpression = "daily_at_${String.format("%02d:%02d", hour, minute)}",
            promptAction = cleanAction,
            isRecurring = isRecurring,
            hourOfDay = hour,
            minute = minute
        )

        taskSchedulerRepository.scheduleTask(task)

        return ToolExecutionResponse(
            toolName = name,
            isSuccess = true,
            output = "Scheduled task successfully registered: '$cleanTitle' to execute ${if (isRecurring) "daily" else "once"} at ${String.format("%02d:%02d", hour, minute)}."
        )
    }

    private fun synthesizeTitle(rawTitle: String, promptAction: String, hour: Int): String {
        val lower = (rawTitle + " " + promptAction).lowercase()
        val timePeriod = when {
            hour in 5..11 -> "Morning"
            hour in 12..16 -> "Afternoon"
            else -> "Evening"
        }

        return when {
            rawTitle.isNotBlank() && !rawTitle.lowercase().startsWith("schedule") -> rawTitle.trim()
            lower.contains("email") && lower.contains("summary") -> "$timePeriod Email Summary"
            lower.contains("email") -> "$timePeriod Email Check & Highlights"
            lower.contains("news") || lower.contains("browse") -> "Daily Tech & News Digest"
            lower.contains("summary") -> "$timePeriod Summary & Progress Review"
            else -> {
                val cleaned = rawTitle.replace(Regex("""(?i)^schedule\s*(\d{1,2}(?::\d{2})?\s*(?:am|pm|p\.m\.|a\.m\.)?)?"""), "").trim()
                if (cleaned.isNotBlank()) cleaned.replaceFirstChar { it.uppercase() }
                else "$timePeriod Scheduled Routine"
            }
        }
    }

    /** The model already writes a complete instruction; never rewrite it with keyword rules. */
    private fun synthesizeAction(rawAction: String): String = rawAction.trim()
}
