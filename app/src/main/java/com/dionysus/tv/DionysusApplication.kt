package com.dionysus.tv

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.dionysus.tv.data.iptv.IptvRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class DionysusApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var iptvRepository: IptvRepository

    // App-lifetime scope for low-priority background warm-up. SupervisorJob so one
    // failed job can't take the rest down; IO dispatcher so nothing touches the UI.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        installCrashCatcher()
        // Start refreshing channels + EPG in the background right away, so the Live TV
        // guide is warm by the time the user navigates to it. Gentle and best-effort:
        // it runs off the main thread and can never delay startup or crash the app.
        appScope.launch { runCatching { iptvRepository.warmUp() } }
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
