package com.dionysus.tv.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.dionysus.tv.core.model.DebridProvider
import com.dionysus.tv.core.model.MediaItem
import com.dionysus.tv.core.model.StreamSource
import com.dionysus.tv.data.debrid.DebridRepository
import com.dionysus.tv.data.local.DownloadStatus
import com.dionysus.tv.data.local.dao.DownloadDao
import com.dionysus.tv.data.local.entity.DownloadEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates local downloads: resolves a scraper source to a direct URL via a
 * debrid provider, records it, and enqueues [DownloadWorker] to fetch the file.
 */
@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val debridRepository: DebridRepository,
) {
    private fun clock(): Long = System.currentTimeMillis()

    fun downloads(): Flow<List<DownloadEntity>> = downloadDao.observeAll()

    /**
     * Start downloading [source] for [item]. Returns the download id, or null if
     * the source could not be resolved to a direct URL by any debrid provider.
     */
    suspend fun startDownload(
        item: MediaItem,
        source: StreamSource,
        preferred: DebridProvider? = null,
    ): String? {
        val id = UUID.randomUUID().toString()
        downloadDao.upsert(
            DownloadEntity(
                id = id,
                mediaId = item.id,
                title = item.title,
                sourceTitle = source.title,
                quality = source.quality.label,
                remoteUrl = source.url.orEmpty(),
                localPath = null,
                posterUrl = item.posterUrl,
                status = DownloadStatus.RESOLVING.name,
                bytesDownloaded = 0,
                totalBytes = source.sizeBytes ?: 0,
                createdAt = clock(),
                workId = null,
            ),
        )

        val directUrl = source.url ?: debridRepository.resolve(source, preferred)?.playbackUrl
        if (directUrl.isNullOrBlank()) {
            downloadDao.updateStatus(id, DownloadStatus.FAILED.name, null)
            return null
        }

        val current = downloadDao.get(id) ?: return null
        downloadDao.upsert(current.copy(remoteUrl = directUrl, status = DownloadStatus.QUEUED.name))

        enqueueWorker(id)
        return id
    }

    fun cancel(download: DownloadEntity) {
        download.workId?.let { WorkManager.getInstance(context).cancelWorkById(UUID.fromString(it)) }
    }

    /** Stop the worker but keep the partial file so it can resume later. */
    suspend fun pause(download: DownloadEntity) {
        download.workId?.let { WorkManager.getInstance(context).cancelWorkById(UUID.fromString(it)) }
        downloadDao.updateStatus(download.id, DownloadStatus.PAUSED.name, download.localPath)
    }

    /** Re-enqueue a paused/failed download; the worker resumes via HTTP range. */
    suspend fun resume(download: DownloadEntity) {
        downloadDao.updateStatus(download.id, DownloadStatus.QUEUED.name, download.localPath)
        enqueueWorker(download.id)
    }

    suspend fun delete(download: DownloadEntity) {
        cancel(download)
        download.localPath?.let { path ->
            runCatching {
                if (path.startsWith("content://")) {
                    // SAF-stored file (user-chosen folder).
                    androidx.documentfile.provider.DocumentFile
                        .fromSingleUri(context, android.net.Uri.parse(path))?.delete()
                } else {
                    java.io.File(path).delete()
                }
            }
        }
        downloadDao.delete(download)
    }

    private suspend fun enqueueWorker(id: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_DOWNLOAD_ID to id))
            .setConstraints(constraints)
            .addTag(TAG)
            .build()
        downloadDao.get(id)?.let { downloadDao.upsert(it.copy(workId = request.id.toString())) }
        WorkManager.getInstance(context)
            .enqueueUniqueWork("download_$id", ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        private const val TAG = "dionysus_download"
    }
}
