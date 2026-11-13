package com.aurviz.agent.domain.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A standard 5-field cron schedule: `minute hour day-of-month month day-of-week`.
 *
 * Supports `*`, `?`, lists (`1,15`), ranges (`1-5`) and steps (a slash, as in 10-30 stepping by 5) and the names
 * `JAN-DEC` and `SUN-SAT`. Day-of-week accepts 0 or 7 for Sunday. As in classic cron, when both
 * day-of-month and day-of-week are restricted, a day matches if **either** matches.
 *
 * The legacy format `daily_at_HH:MM` used by earlier builds is also understood.
 */
class CronSchedule private constructor(
    val expression: String,
    private val minutes: Set<Int>,
    private val hours: Set<Int>,
    private val daysOfMonth: Set<Int>,
    private val months: Set<Int>,
    /** 1 = Monday ... 7 = Sunday (java.time numbering). */
    private val daysOfWeek: Set<Int>,
    private val domRestricted: Boolean,
    private val dowRestricted: Boolean
) {

    /** Next time strictly after [afterEpochMillis] that matches, or null if none within 8 years. */
    fun nextAfter(afterEpochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val start = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(afterEpochMillis), zone)
            .withSecond(0).withNano(0).plusMinutes(1)
        var date: LocalDate = start.toLocalDate()
        val lastDate = date.plusYears(8)
        var first = true
        while (!date.isAfter(lastDate)) {
            if (dayMatches(date)) {
                for (h in hours.sorted()) {
                    for (m in minutes.sorted()) {
                        if (first && (h < start.hour || (h == start.hour && m < start.minute))) continue
                        // atZone resolves daylight-saving gaps to the next valid instant.
                        val candidate = date.atTime(h, m).atZone(zone)
                        if (candidate.toInstant().toEpochMilli() > afterEpochMillis) {
                            return candidate.toInstant().toEpochMilli()
                        }
                    }
                }
            }
            date = date.plusDays(1)
            first = false
        }
        return null
    }

    private fun dayMatches(date: LocalDate): Boolean {
        if (date.monthValue !in months) return false
        val domOk = date.dayOfMonth in daysOfMonth
        val dowOk = date.dayOfWeek.value in daysOfWeek
        return when {
            domRestricted && dowRestricted -> domOk || dowOk
            domRestricted -> domOk
            dowRestricted -> dowOk
            else -> true
        }
    }

    /** True when the time fields are one fixed hour and minute (the common "at 09:00" case). */
    val fixedTime: Pair<Int, Int>?
        get() = if (hours.size == 1 && minutes.size == 1) hours.first() to minutes.first() else null

    /** The same schedule at a different fixed time, or null when the time is not a single hour:minute. */
    fun withTime(hour: Int, minute: Int): CronSchedule? {
        if (fixedTime == null || hour !in 0..23 || minute !in 0..59) return null
        val parts = expression.trim().split(Regex("\\s+")).toMutableList()
        if (parts.size != 5) return null
        parts[0] = minute.toString()
        parts[1] = hour.toString()
        return parse(parts.joinToString(" "))
    }

    /** Short human description, for example "Weekdays at 09:00" or "Every Monday at 18:30". */
    fun describe(): String {
        val time = fixedTime?.let { (h, m) -> String.format("%02d:%02d", h, m) }
        val dayPart = when {
            !dowRestricted && !domRestricted && months.size == 12 -> "Daily"
            dowRestricted && !domRestricted && months.size == 12 -> describeDays(daysOfWeek)
            else -> null
        }
        return when {
            dayPart != null && time != null -> "$dayPart at $time"
            time != null -> "Scheduled at $time ($expression)"
            else -> "Schedule: $expression"
        }
    }

    private fun describeDays(days: Set<Int>): String = when (days) {
        setOf(1, 2, 3, 4, 5) -> "Weekdays"
        setOf(6, 7) -> "Weekends"
        else -> {
            val names = days.sorted().map { DayOfWeek.of(it).name.lowercase().replaceFirstChar(Char::uppercase) }
            if (names.size == 1) "Every ${names[0]}" else names.joinToString(", ") { it.take(3) }
        }
    }

    companion object {
        private val MONTH_NAMES = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        private val DAY_NAMES = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
        private val LEGACY = Regex("^daily_at_(\\d{1,2}):(\\d{2})$")

        /** Parses [text]; returns null when it is not a valid schedule. */
        fun parse(text: String): CronSchedule? {
            val trimmed = text.trim()
            LEGACY.matchEntire(trimmed)?.let {
                val h = it.groupValues[1].toInt()
                val m = it.groupValues[2].toInt()
                return if (h in 0..23 && m in 0..59) parse("$m $h * * *") else null
            }
            val fields = trimmed.split(Regex("\\s+"))
            if (fields.size != 5) return null
            val minutes = parseField(fields[0], 0, 59) ?: return null
            val hours = parseField(fields[1], 0, 23) ?: return null
            val dom = parseField(fields[2], 1, 31) ?: return null
            val months = parseField(fields[3], 1, 12, MONTH_NAMES, nameOffset = 1) ?: return null
            val dowRaw = parseField(fields[4], 0, 7, DAY_NAMES, nameOffset = 0) ?: return null
            // 0 and 7 are both Sunday; java.time uses 7.
            val dow = dowRaw.map { if (it == 0) 7 else it }.toSet()
            return CronSchedule(
                expression = trimmed,
                minutes = minutes,
                hours = hours,
                daysOfMonth = dom,
                months = months,
                daysOfWeek = dow,
                domRestricted = !isWildcard(fields[2]),
                dowRestricted = !isWildcard(fields[4])
            )
        }

        private fun isWildcard(field: String) = field == "*" || field == "?"

        private fun parseField(
            field: String,
            min: Int,
            max: Int,
            names: List<String>? = null,
            nameOffset: Int = 0
        ): Set<Int>? {
            val result = sortedSetOf<Int>()
            for (part in field.split(',')) {
                if (part.isEmpty()) return null
                val stepSplit = part.split('/')
                if (stepSplit.size > 2) return null
                val step = if (stepSplit.size == 2) stepSplit[1].toIntOrNull()?.takeIf { it > 0 } ?: return null else 1
                val range = stepSplit[0]
                val (from, to) = when {
                    range == "*" || range == "?" -> min to max
                    '-' in range -> {
                        val ends = range.split('-')
                        if (ends.size != 2) return null
                        (value(ends[0], names, nameOffset) ?: return null) to (value(ends[1], names, nameOffset) ?: return null)
                    }
                    else -> {
                        val single = value(range, names, nameOffset) ?: return null
                        // "5/10" means from 5 to the maximum in steps of 10; a bare number is just itself.
                        single to (if (stepSplit.size == 2) max else single)
                    }
                }
                if (from < min || to > max || from > to) return null
                var v = from
                while (v <= to) { result += v; v += step }
            }
            return result.ifEmpty { null }
        }

        private fun value(token: String, names: List<String>?, nameOffset: Int): Int? {
            token.toIntOrNull()?.let { return it }
            val index = names?.indexOf(token.uppercase().take(3)) ?: -1
            return if (index >= 0 && token.length in 3..9) index + nameOffset else null
        }

        /**
         * Builds a schedule from the friendly recurrence words the agent uses plus a time of day.
         * [recurrence] may be "daily", "weekdays", "weekends", day names ("MON,WED,FRI", "Monday"),
         * or a full 5-field cron expression (then [hour] and [minute] are ignored).
         */
        fun fromRecurrence(recurrence: String?, hour: Int, minute: Int): CronSchedule? {
            if (hour !in 0..23 || minute !in 0..59) return null
            val word = recurrence?.trim()?.lowercase().orEmpty()
            val dow = when (word) {
                "", "daily", "everyday", "every day", "every_day" -> "*"
                "weekday", "weekdays" -> "1-5"
                "weekend", "weekends" -> "0,6"
                else -> {
                    parse(recurrence!!)?.let { return it } // a full cron expression
                    val days = word.split(Regex("[,\\s/&]+|\\band\\b")).filter { it.isNotBlank() }
                        .map { token -> DAY_NAMES.indexOfFirst { token.take(3).equals(it, ignoreCase = true) } }
                    if (days.isEmpty() || days.any { it < 0 }) return null
                    days.distinct().sorted().joinToString(",")
                }
            }
            return parse("$minute $hour * * $dow")
        }
    }
}
