package com.example.phoneagent

import android.app.Application
import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.logging.crashLoggingHandler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PhoneAgentApplication : Application() {
    @Inject
    lateinit var logger: AppLogger

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(crashLoggingHandler(logger, Thread.getDefaultUncaughtExceptionHandler()))
    }
}
