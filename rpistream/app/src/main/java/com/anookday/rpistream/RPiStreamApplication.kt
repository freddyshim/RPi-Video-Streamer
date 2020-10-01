package com.anookday.rpistream

import android.app.Application
import timber.log.Timber

class RPiStreamApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}