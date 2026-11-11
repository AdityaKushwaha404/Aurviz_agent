package com.aurviz.agent.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeUtilsTest {

    @Test
    fun parseHourAndMinute_standard12HourAM() {
        val (hour, minute) = TimeUtils.parseHourAndMinute("9:30 AM")
        assertEquals(9, hour)
        assertEquals(30, minute)
    }

    @Test
    fun parseHourAndMinute_standard12HourPM() {
        val (hour, minute) = TimeUtils.parseHourAndMinute("2:15 PM")
        assertEquals(14, hour)
        assertEquals(15, minute)
    }

    @Test
    fun parseHourAndMinute_naturalLanguageMorning() {
        val (hour, minute) = TimeUtils.parseHourAndMinute("every morning at 9 am")
        assertEquals(9, hour)
        assertEquals(0, minute)
    }

    @Test
    fun parseHourAndMinute_24HourTime() {
        val (hour, minute) = TimeUtils.parseHourAndMinute("18:45")
        assertEquals(18, hour)
        assertEquals(45, minute)
    }

    @Test
    fun parseHourAndMinute_fallbackDefaultsTo9AM() {
        val (hour, minute) = TimeUtils.parseHourAndMinute("some unparseable random string")
        assertEquals(9, hour)
        assertEquals(0, minute)
    }
}
