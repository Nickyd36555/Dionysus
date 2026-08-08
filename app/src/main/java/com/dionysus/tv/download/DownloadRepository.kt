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
import com.dionysus.tv.data.settings.SettingsRepository
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
    private val settings: SettingsRepository,
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
        // Delete the known completed/paused file…
        download.localPath?.let { deletePath(it) }
        // …AND any orphaned partial from a failed/queued download, whose localPath is
        // null but whose bytes are still on disk under a predictable name. This is why
        // failed downloads could not be "deleted off disk".
        val fileName = fileNameFor(download.title, download.id)
        runCatching {
            val f = java.io.File(java.io.File(context.getExternalFilesDir(null), "downloads"), fileName)
            if (f.exists()) f.delete()
        }
        val folderUri = runCatching { settings.currentDownloadFolderUri() }.getOrNull()
        if (!folderUri.isNullOrBlank()) runCatching {
            androidx.documentfile.provider.DocumentFile
                .fromTreeUri(context, android.net.Uri.parse(folderUri))
                ?.findFile(fileName)?.delete()
        }
        downloadDao.delete(download)
    }

    private fun deletePath(path: String) {
        runCatching {
            if (path.startsWith("content://")) {
                androidx.documentfile.provider.DocumentFile
                    .fromSingleUri(context, android.net.Uri.parse(path))?.delete()
            } else {
                java.io.File(path).delete()
            }
        }
    }

    /** Same naming the worker uses, so orphaned partials can be found and removed. */
    private fun fileNameFor(title: String, id: String): String {
        val safe = title.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)
        return "${safe}_$id.mp4"
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
        // REPLACE (not KEEP) so retrying a failed/paused download always re-runs
        // instead of being ignored because a stale work with the same name exists.
        WorkManager.getInstance(context)
            .enqueueUniqueWork("download_$id", ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        private const val TAG = "dionysus_download"
    }
}
