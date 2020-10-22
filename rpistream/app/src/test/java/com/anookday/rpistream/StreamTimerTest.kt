package com.anookday.rpistream

import com.anookday.rpistream.stream.StreamTimer
import org.junit.Test

import org.junit.Assert.*

/**
 * Unit test for [StreamTimer].
 */
class StreamTimerTest {
    private val timer = object : StreamTimer() {
        override fun updateNotification() {}
    }

    @Test
    fun testTimeElapsed4300() {
        timer.timeElapsed = 4300
        assertEquals("01:11:40", timer.getTimeElapsedString())
    }
}