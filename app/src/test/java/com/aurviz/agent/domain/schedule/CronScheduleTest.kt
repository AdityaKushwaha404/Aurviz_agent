package com.aurviz.agent.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class CronScheduleTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    /** 2026-11-13 is a Friday. */
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()

    private fun next(expr: String, after: Long): ZonedDateTime =
        ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(CronSchedule.parse(expr)!!.nextAfter(after, zone)!!), zone
        )

    @Test
    fun dailyRunsTodayIfStillAheadElseTomorrow() {
        val morning = next("0 9 * * *", at(2026, 11, 13, 8, 0))
        assertEquals(13, morning.dayOfMonth); assertEquals(9, morning.hour)
        val afterNine = next("0 9 * * *", at(2026, 11, 13, 9, 0))
        assertEquals(14, afterNine.dayOfMonth)
    }

    @Test
    fun weekdaysSkipTheWeekend() {
        // Friday 10:00 after the 09:00 slot: next weekday is Monday 16 Nov.
        val n = next("0 9 * * 1-5", at(2026, 11, 13, 10, 0))
        assertEquals(16, n.dayOfMonth); assertEquals(java.time.DayOfWeek.MONDAY, n.dayOfWeek)
    }

    @Test
    fun namedDaysAndSundayAsZeroOrSeven() {
        assertEquals(java.time.DayOfWeek.MONDAY, next("30 18 * * MON", at(2026, 11, 13, 10, 0)).dayOfWeek)
        assertEquals(java.time.DayOfWeek.SUNDAY, next("0 8 * * 0", at(2026, 11, 13, 10, 0)).dayOfWeek)
        assertEquals(java.time.DayOfWeek.SUNDAY, next("0 8 * * 7", at(2026, 11, 13, 10, 0)).dayOfWeek)
        val multi = next("0 9 * * MON,WED,FRI", at(2026, 11, 13, 10, 0))
        assertEquals(java.time.DayOfWeek.MONDAY, multi.dayOfWeek)
    }

    @Test
    fun stepsAndRanges() {
        val n = next("*/15 * * * *", at(2026, 11, 13, 10, 7))
        assertEquals(10, n.hour); assertEquals(15, n.minute)
        val range = next("0 9-11 * * *", at(2026, 11, 13, 9, 30))
        assertEquals(10, range.hour)
    }

    @Test
    fun dayOfMonthAndMonthFilters() {
        val n = next("0 9 1 * *", at(2026, 11, 13, 10, 0))
        assertEquals(12, n.monthValue); assertEquals(1, n.dayOfMonth)
        val jan = next("0 0 1 JAN *", at(2026, 11, 13, 10, 0))
        assertEquals(2027, jan.year); assertEquals(1, jan.monthValue)
    }

    @Test
    fun dayOfMonthOrDayOfWeekWhenBothRestrictedLikeClassicCron() {
        // 13th of the month OR any Monday: from Fri 2026-11-13 00:00 the 13th itself matches.
        val n = next("0 9 13 * MON", at(2026, 11, 13, 0, 0))
        assertEquals(13, n.dayOfMonth)
    }

    @Test
    fun rejectsInvalidExpressions() {
        listOf("", "* * * *", "61 * * * *", "0 24 * * *", "0 9 * * FUNDAY", "0 9 * * 1-", "a b c d e", "0 9 32 * *", "*/0 * * * *")
            .forEach { assertNull(it, CronSchedule.parse(it)) }
    }

    @Test
    fun legacyDailyFormatStillWorks() {
        val s = CronSchedule.parse("daily_at_09:30")
        assertNotNull(s)
        assertEquals(9 to 30, s!!.fixedTime)
        assertEquals("Daily at 09:30", s.describe())
    }

    @Test
    fun friendlyRecurrenceWords() {
        assertEquals("Weekdays at 09:00", CronSchedule.fromRecurrence("weekdays", 9, 0)!!.describe())
        assertEquals("Weekends at 10:15", CronSchedule.fromRecurrence("weekends", 10, 15)!!.describe())
        assertEquals("Daily at 21:00", CronSchedule.fromRecurrence("daily", 21, 0)!!.describe())
        assertEquals("Daily at 07:05", CronSchedule.fromRecurrence(null, 7, 5)!!.describe())
        assertEquals("Every Monday at 09:00", CronSchedule.fromRecurrence("Monday", 9, 0)!!.describe())
        assertEquals("Mon, Wed, Fri at 18:30", CronSchedule.fromRecurrence("MON, WED and FRI", 18, 30)!!.describe())
        assertEquals("0 6 * * 1-5", CronSchedule.fromRecurrence("0 6 * * 1-5", 9, 0)!!.expression)
        assertNull(CronSchedule.fromRecurrence("every blursday", 9, 0))
        assertNull(CronSchedule.fromRecurrence("daily", 25, 0))
    }

    @Test
    fun withTimeKeepsTheDaysAndChangesTheClock() {
        val moved = CronSchedule.parse("0 9 * * 1-5")!!.withTime(18, 45)!!
        assertEquals("Weekdays at 18:45", moved.describe())
        assertNull(CronSchedule.parse("*/15 * * * *")!!.withTime(9, 0))
    }
}
