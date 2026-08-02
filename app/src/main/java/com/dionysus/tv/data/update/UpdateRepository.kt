package com.dionysus.tv.data.update

import android.util.Log
import com.dionysus.tv.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/** Details of an available newer release. */
data class UpdateInfo(
    val versionName: String,
    val notes: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)

/**
 * Checks the configured GitHub repo's latest release and reports whether a
 * newer versioned APK is available. The repo coordinates come from BuildConfig
 * so forks can point self-update at their own releases.
 */
@Singleton
class UpdateRepository @Inject constructor(
    private val api: GitHubApi,
) {
    suspend fun checkForUpdate(): UpdateInfo? {
        val release = runCatching {
            api.latestRelease(BuildConfig.UPDATE_OWNER, BuildConfig.UPDATE_REPO)
        }.getOrElse {
            Log.w(TAG, "Update check failed", it)
            return null
        }

        val latest = release.tagName.trimStart('v', 'V')
        if (latest.isBlank() || !isNewer(latest, BuildConfig.VERSION_NAME)) return null

        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: return null

        return UpdateInfo(
            versionName = latest,
            notes = release.body.take(500),
            downloadUrl = apk.browserDownloadUrl,
            sizeBytes = apk.size,
        )
    }

    /** True when [latest] is a higher dotted-numeric version than [current]. */
    internal fun isNewer(latest: String, current: String): Boolean {
        val l = latest.toParts()
        val c = current.toParts()
        for (i in 0 until maxOf(l.size, c.size)) {
            val a = l.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun String.toParts(): List<Int> =
        split(".", "-").mapNotNull { it.filter(Char::isDigit).toIntOrNull() }

    companion object {
        private const val TAG = "UpdateRepository"
    }
}
