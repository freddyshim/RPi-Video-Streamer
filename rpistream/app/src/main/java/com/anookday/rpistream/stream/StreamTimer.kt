package com.anookday.rpistream.stream

import android.os.CountDownTimer

private const val INTERVAL_MS: Long = 1000
private const val DURATION: Long = 30 * INTERVAL_MS
private const val HOUR: Int = 60 * 60
private const val MINUTE: Int = 60
private const val SECOND: Int = 1

/**
 * Timer class that shows how long a user has been streaming.
 */
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
        return when {
            timeElapsed > 0 -> {
                "%02d:%02d:%02d".format(
                    timeElapsed / HOUR,
                    timeElapsed / MINUTE % MINUTE,
                    timeElapsed % HOUR % MINUTE
                )
            }
            timeElapsed >= 360000 -> {
                "%03d:%02d:%02d".format(
                    timeElapsed / HOUR,
                    timeElapsed / MINUTE % MINUTE,
                    timeElapsed % HOUR % MINUTE
                )
            }
            else -> {
                "00:00:00"
            }
        }
    }

    fun reset() {
        timeElapsed = 0
        cancel()
    }
}
