package com.dionysus.tv

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class DionysusApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        installCrashCatcher()
    }

    /**
     * Persist any uncaught crash's stack trace so MainActivity can show it on the
     * next launch. This makes "it just crashes" diagnosable — even for crashes that
     * happen at startup — without needing a wired-up laptop / logcat. The system's
     * default handler still runs afterwards, so behavior is otherwise unchanged.
     */
    private fun installCrashCatcher() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val trace = android.util.Log.getStackTraceString(throwable)
                File(filesDir, CRASH_FILE).writeText(
                    "Dionysus v${BuildConfig.VERSION_NAME}\n$throwable\n\n$trace",
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        const val CRASH_FILE = "last_crash.txt"
    }
}
