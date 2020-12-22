package com.anookday.rpistream

import com.anookday.rpistream.stream.StreamTimer
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before

/**
 * Unit test for [StreamTimer].
 */
class StreamTimerTest {
    private val timer = object : StreamTimer() {
        override fun updateNotification() {}
    }

    @Before
    fun setup() {
        timer.timeElapsed = 0
    }

    @Test
    fun testTimeElapsedZero() {
        timer.timeElapsed = 0
        assertEquals("00:00:00", timer.getTimeElapsedString())
    }

    @Test
    fun testTimeElapsedSeconds() {
        timer.timeElapsed = 1
        assertEquals("00:00:01", timer.getTimeElapsedString())
        timer.timeElapsed = 42
        assertEquals("00:00:42", timer.getTimeElapsedString())
        timer.timeElapsed = 59
        assertEquals("00:00:59", timer.getTimeElapsedString())
    }

    @Test
    fun testTimeElapsedMinutes() {
        timer.timeElapsed = 60
        assertEquals("00:01:00", timer.getTimeElapsedString())
        timer.timeElapsed = 420
        assertEquals("00:07:00", timer.getTimeElapsedString())
        timer.timeElapsed = 3540
        assertEquals("00:59:00", timer.getTimeElapsedString())
    }

    @Test
    fun testTimeElapsedMinutesSeconds() {
        timer.timeElapsed = 61
        assertEquals("00:01:01", timer.getTimeElapsedString())
        timer.timeElapsed = 260
        assertEquals("00:04:20", timer.getTimeElapsedString())
        timer.timeElapsed = 3599
        assertEquals("00:59:59", timer.getTimeElapsedString())
    }

    @Test
    fun getTimeElapsedHours() {
        timer.timeElapsed = 3600
        assertEquals("01:00:00", timer.getTimeElapsedString())
        timer.timeElapsed = 28800
        assertEquals("08:00:00", timer.getTimeElapsedString())
        timer.timeElapsed = 86400
        assertEquals("24:00:00", timer.getTimeElapsedString())
        timer.timeElapsed = 356400
        assertEquals("99:00:00", timer.getTimeElapsedString())
        timer.timeElapsed = 360000
        assertEquals("100:00:00", timer.getTimeElapsedString())
        timer.timeElapsed = 3596400
        assertEquals("999:00:00", timer.getTimeElapsedString())
    }

    @Test
    fun testTimeElapsedHoursMinutesSeconds() {
        timer.timeElapsed = 4300
        assertEquals("01:11:40", timer.getTimeElapsedString())
        timer.timeElapsed = 12345
        assertEquals("03:25:45", timer.getTimeElapsedString())
        timer.timeElapsed = 123456
        assertEquals("34:17:36", timer.getTimeElapsedString())
    }

    @Test
    fun getTimeElapsedNegative() {
        timer.timeElapsed = -1
        assertEquals("00:00:00", timer.getTimeElapsedString())
        timer.timeElapsed = -123
        assertEquals("00:00:00", timer.getTimeElapsedString())
        timer.timeElapsed = -12345
        assertEquals("00:00:00", timer.getTimeElapsedString())
    }
}