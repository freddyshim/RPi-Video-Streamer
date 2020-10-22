package com.anookday.rpistream.stream

import android.os.CountDownTimer

private const val INTERVAL_MS: Long = 1000
private const val DURATION: Long = 30 * INTERVAL_MS
private const val HOUR: Int = 60 * 60
private const val MINUTE: Int = 60
private const val SECOND: Int = 1

abstract class StreamTimer() : CountDownTimer(DURATION, INTERVAL_MS) {
    var timeElapsed: Int = 0

    override fun onTick(millisUntilFinished: Long) {
        timeElapsed++
        updateNotification()
    }

    override fun onFinish() {
        start()
    }

    abstract fun updateNotification()

    fun getTimeElapsedString(): String {
        return "%02d:%02d:%02d".format(
            timeElapsed / HOUR,
            timeElapsed / MINUTE % MINUTE,
            timeElapsed % HOUR % MINUTE
        )
    }

    fun reset() {
        timeElapsed = 0
        cancel()
    }
}
