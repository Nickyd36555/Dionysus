package com.dionysus.tv.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.dionysus.tv.R
import com.dionysus.tv.data.local.DownloadStatus
import com.dionysus.tv.data.local.dao.DownloadDao
import com.dionysus.tv.data.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * Streams a resolved direct URL to app-scoped external storage, reporting byte
 * progress to Room so the Downloads screen can show a live progress bar. Runs
 * as a foreground worker so long downloads survive the app going to background.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val downloadDao: DownloadDao,
    private val okHttpClient: OkHttpClient,
    private val settings: SettingsRepository,
) : CoroutineWorker(appContext, params) {

    /**
     * A download-only client: the shared client's 60s callTimeout would kill any
     * download longer than a minute (which is basically all of them). Here there is
     * no overall call timeout; a read timeout still detects a genuinely dead stall.
     */
    private val downloadClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return@withContext Result.failure()
        val entity = downloadDao.get(downloadId) ?: return@withContext Result.failure()

        val url = entity.remoteUrl.takeIf { it.isNotBlank() } ?: run {
            downloadDao.updateStatus(downloadId, DownloadStatus.FAILED.name, null)
            return@withContext Result.failure()
        }

        setForeground(foregroundInfo(entity.title))
        // Target is either the user's chosen SAF folder (USB/SD/network) or app storage.
        val target = resolveTarget(entity.title, downloadId)

        try {
            downloadDao.updateStatus(downloadId, DownloadStatus.DOWNLOADING.name, null)

            // Resume support: if a partial file exists, ask for the remaining bytes.
            val existing = target.existingBytes()
            val builder = Request.Builder().url(url)
            if (existing > 0) builder.header("Range", "bytes=$existing-")

            downloadClient.newCall(builder.build()).execute().use { response ->
                // 416 = we already have the whole file.
                if (response.code == 416) {
                    downloadDao.updateStatus(downloadId, DownloadStatus.COMPLETED.name, target.pathString)
                    return@withContext Result.success()
                }
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                val body = response.body ?: throw IllegalStateException("Empty body")

                // 206 = server honored the range and we append; otherwise restart.
                val append = existing > 0 && response.code == 206
                var downloaded = if (append) existing else 0L
                val total = if (append) existing + body.contentLength() else body.contentLength()
                    .takeIf { it > 0 } ?: entity.totalBytes

                body.byteStream().buffered(BUFFER_BYTES).use { input ->
                    target.openOutput(append).buffered(BUFFER_BYTES).use { output ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        var read: Int
                        var lastReported = downloaded
                        while (input.read(buffer).also { read = it } != -1) {
                            if (isStopped) {
                                // Keep the partial file so Resume can continue it.
                                downloadDao.updateStatus(downloadId, DownloadStatus.PAUSED.name, null)
                                return@withContext Result.failure()
                            }
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (downloaded - lastReported > PROGRESS_INTERVAL_BYTES) {
                                downloadDao.updateProgress(
                                    downloadId, DownloadStatus.DOWNLOADING.name, downloaded, total,
                                )
                                lastReported = downloaded
                            }
                        }
                    }
                }
                downloadDao.updateProgress(downloadId, DownloadStatus.DOWNLOADING.name, downloaded, total)
            }
            downloadDao.updateStatus(downloadId, DownloadStatus.COMPLETED.name, target.pathString)
            Result.success()
        } catch (t: Throwable) {
            // Preserve the partial file for resume; only mark failed.
            downloadDao.updateStatus(downloadId, DownloadStatus.FAILED.name, null)
            Result.failure()
        }
    }

    /** Where the bytes land: a resumable OutputStream + how many bytes exist so far. */
    private interface Target {
        fun existingBytes(): Long
        fun openOutput(append: Boolean): OutputStream
        val pathString: String
    }

    private suspend fun resolveTarget(title: String, id: String): Target {
        val safeName = title.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)
        val fileName = "${safeName}_$id.mp4"
        val folderUri = runCatching { settings.currentDownloadFolderUri() }.getOrNull()
        if (!folderUri.isNullOrBlank()) {
            val doc = runCatching {
                val tree = DocumentFile.fromTreeUri(applicationContext, Uri.parse(folderUri))
                if (tree != null && tree.canWrite()) {
                    tree.findFile(fileName) ?: tree.createFile("video/mp4", fileName)
                } else null
            }.getOrNull()
            if (doc != null) return SafTarget(doc.uri)
            // Fall back to app storage if the chosen folder is unavailable.
        }
        val dir = File(applicationContext.getExternalFilesDir(null), "downloads").apply { mkdirs() }
        return FileTarget(File(dir, fileName))
    }

    private inner class FileTarget(private val file: File) : Target {
        init { file.parentFile?.mkdirs() }
        override fun existingBytes(): Long = if (file.exists()) file.length() else 0L
        override fun openOutput(append: Boolean): OutputStream = java.io.FileOutputStream(file, append)
        override val pathString: String get() = file.absolutePath
    }

    private inner class SafTarget(private val uri: Uri) : Target {
        override fun existingBytes(): Long = runCatching {
            applicationContext.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize.coerceAtLeast(0L)
            } ?: 0L
        }.getOrDefault(0L)

        override fun openOutput(append: Boolean): OutputStream =
            applicationContext.contentResolver.openOutputStream(uri, if (append) "wa" else "w")
                ?: throw IllegalStateException("Cannot open output for $uri")

        override val pathString: String get() = uri.toString()
    }

    private fun foregroundInfo(title: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Downloading")
            .setContentText(title)
            .setSmallIcon(R.drawable.app_banner)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1001
        private const val PROGRESS_INTERVAL_BYTES = 1_000_000L
        // Large buffer + buffered streams cut syscall overhead for much faster writes.
        private const val BUFFER_BYTES = 256 * 1024
    }
}
