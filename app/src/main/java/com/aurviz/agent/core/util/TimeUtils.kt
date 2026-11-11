package com.aurviz.agent.core.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeUtils {

    private val dateTimeFormatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    private val timeOnlyFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun formatDateTime(epochMillis: Long): String {
        return dateTimeFormatter.format(Date(epochMillis))
    }

    fun formatTime(epochMillis: Long): String {
        return timeOnlyFormatter.format(Date(epochMillis))
    }

    /**
     * Calculates the initial delay in milliseconds from now until the target hour and minute.
     * E.g. "9 AM" -> calculate delay from current time until next 9:00 AM.
     */
    fun calculateInitialDelayForTime(hourOfDay: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        return target.timeInMillis - now.timeInMillis
    }

    /**
     * Parses standard natural time strings (e.g., "9 AM", "14:30", "every morning at 9")
     * Returns Pair(hour, minute). Defaults to (9, 0) if unparseable.
     */
    fun parseHourAndMinute(input: String): Pair<Int, Int> {
        val lower = input.lowercase()
        val regex12hr = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)""")
        val match12 = regex12hr.find(lower)
        if (match12 != null) {
            var hour = match12.groupValues[1].toInt()
            val min = match12.groupValues[2].ifEmpty { "0" }.toInt()
            val meridiem = match12.groupValues[3]
            if (meridiem == "pm" && hour < 12) hour += 12
            if (meridiem == "am" && hour == 12) hour = 0
            return Pair(hour, min)
        }

        val regex24hr = Regex("""(\d{1,2}):(\d{2})""")
        val match24 = regex24hr.find(lower)
        if (match24 != null) {
            val hour = match24.groupValues[1].toInt()
            val min = match24.groupValues[2].toInt()
            return Pair(hour, min)
        }

        return Pair(9, 0) // Default fallback 9:00 AM
    }
}
