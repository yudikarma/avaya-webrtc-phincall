package com.phincall.webrtc

import android.app.Application
import com.phincall.webrtc.logging.DiagnosticLogManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PhinCallApplication : Application() {

    @Inject
    lateinit var diagnosticLogManager: DiagnosticLogManager

    override fun onCreate() {
        super.onCreate()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                diagnosticLogManager.recordCrash(thread.name, throwable)
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}
