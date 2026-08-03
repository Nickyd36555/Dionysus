package com.dionysus.tv.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.dionysus.tv.R
import com.dionysus.tv.data.local.DownloadStatus
import com.dionysus.tv.data.local.dao.DownloadDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

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
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return@withContext Result.failure()
        val entity = downloadDao.get(downloadId) ?: return@withContext Result.failure()

        val url = entity.remoteUrl.takeIf { it.isNotBlank() } ?: run {
            downloadDao.updateStatus(downloadId, DownloadStatus.FAILED.name, null)
            return@withContext Result.failure()
        }

        setForeground(foregroundInfo(entity.title))
        val target = destinationFile(entity.title, downloadId)

        try {
            downloadDao.updateStatus(downloadId, DownloadStatus.DOWNLOADING.name, null)

            // Resume support: if a partial file exists, ask for the remaining bytes.
            val existing = if (target.exists()) target.length() else 0L
            val builder = Request.Builder().url(url)
            if (existing > 0) builder.header("Range", "bytes=$existing-")

            okHttpClient.newCall(builder.build()).execute().use { response ->
                // 416 = we already have the whole file.
                if (response.code == 416) {
                    downloadDao.updateStatus(downloadId, DownloadStatus.COMPLETED.name, target.absolutePath)
                    return@withContext Result.success()
                }
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                val body = response.body ?: throw IllegalStateException("Empty body")

                // 206 = server honored the range and we append; otherwise restart.
                val append = existing > 0 && response.code == 206
                var downloaded = if (append) existing else 0L
                val total = if (append) existing + body.contentLength() else body.contentLength()
                    .takeIf { it > 0 } ?: entity.totalBytes

                body.byteStream().use { input ->
                    java.io.FileOutputStream(target, append).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
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
            downloadDao.updateStatus(downloadId, DownloadStatus.COMPLETED.name, target.absolutePath)
            Result.success()
        } catch (t: Throwable) {
            // Preserve the partial file for resume; only mark failed.
            downloadDao.updateStatus(downloadId, DownloadStatus.FAILED.name, null)
            Result.failure()
        }
    }

    private fun destinationFile(title: String, id: String): File {
        val dir = File(applicationContext.getExternalFilesDir(null), "downloads").apply { mkdirs() }
        val safeName = title.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)
        return File(dir, "${safeName}_$id.mp4")
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
    }
}
