package com.aurviz.agent.data.tools

import com.aurviz.agent.core.util.TimeUtils
import com.aurviz.agent.domain.model.ScheduledTask
import com.aurviz.agent.domain.model.ToolExecutionResponse
import com.aurviz.agent.domain.repository.TaskSchedulerRepository
import com.aurviz.agent.domain.schedule.CronSchedule
import com.aurviz.agent.domain.tool.AgentTool
import java.util.UUID

class SchedulerTool(
    private val taskSchedulerRepository: TaskSchedulerRepository
) : AgentTool {

    override val name: String = "schedule_task"
    override val description: String = "Schedule a background task that runs while the app is closed. " +
            "Provide: 1) 'title' (a short capitalized name); " +
            "2) 'promptAction' (a complete instruction for what the agent should do when it runs); " +
            "3) 'timeExpression' (time of day such as '9:00 AM' or '21:00'); " +
            "4) 'recurrence': 'daily', 'weekdays' (Monday to Friday), 'weekends', a list of days such as 'MON,WED,FRI' or 'Monday', " +
            "'once' for a single run, or a 5-field cron expression such as '0 9 * * 1-5' (minute hour day-of-month month day-of-week)."

    override suspend fun execute(arguments: Map<String, Any?>): ToolExecutionResponse {
        val rawPromptAction = arguments["promptAction"] as? String
            ?: return ToolExecutionResponse(name, false, "Missing 'promptAction' parameter.")
        val rawTitle = arguments["title"] as? String ?: ""
        val recurrence = (arguments["recurrence"] as? String)?.trim()
        val onceWords = setOf("once", "one-time", "one time", "onetime", "today", "tomorrow")
        val isRecurring = when {
            recurrence != null && recurrence.lowercase() in onceWords -> false
            recurrence != null && recurrence.isNotBlank() -> true
            else -> (arguments["isRecurring"] as? Boolean) ?: true
        }

        // A full cron expression carries its own time; otherwise combine the recurrence with the time of day.
        val fullCron = recurrence?.takeIf { it.isNotBlank() }?.let { CronSchedule.parse(it) }
        val (hour, minute) = fullCron?.fixedTime
            ?: TimeUtils.tryParseHourAndMinute(arguments["timeExpression"] as? String ?: "9:00 AM")
            ?: return ToolExecutionResponse(name, false, "Please provide a valid time, such as 9:00 AM or 21:00.")

        val schedule = when {
            !isRecurring -> CronSchedule.fromRecurrence("daily", hour, minute)
            fullCron != null -> fullCron
            else -> CronSchedule.fromRecurrence(recurrence, hour, minute)
        } ?: return ToolExecutionResponse(
            name, false,
            "I could not understand the repeat pattern '$recurrence'. Use daily, weekdays, weekends, day names like MON,WED,FRI, or a cron expression."
        )

        val cleanTitle = synthesizeTitle(rawTitle, rawPromptAction, hour)
        val cleanAction = synthesizeAction(rawPromptAction)

        val task = ScheduledTask(
            id = UUID.randomUUID().toString(),
            title = cleanTitle,
            cronExpression = schedule.expression,
            promptAction = cleanAction,
            isRecurring = isRecurring,
            hourOfDay = hour,
            minute = minute
        )

        taskSchedulerRepository.scheduleTask(task)

        val when_ = if (isRecurring) schedule.describe() else "once at ${String.format("%02d:%02d", hour, minute)}"
        return ToolExecutionResponse(
            toolName = name,
            isSuccess = true,
            output = "Scheduled task successfully registered: '$cleanTitle' (${when_})."
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
