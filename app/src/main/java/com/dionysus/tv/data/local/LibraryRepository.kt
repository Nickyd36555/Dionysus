package com.dionysus.tv.data.local

import com.dionysus.tv.core.model.MediaItem
import com.dionysus.tv.core.model.MediaType
import com.dionysus.tv.data.local.dao.FavoriteDao
import com.dionysus.tv.data.local.dao.WatchProgressDao
import com.dionysus.tv.data.local.entity.FavoriteEntity
import com.dionysus.tv.data.local.entity.WatchProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** User library: "My List" favorites and "Continue Watching" progress. */
@Singleton
class LibraryRepository @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val watchProgressDao: WatchProgressDao,
) {
    private fun clock(): Long = System.currentTimeMillis()

    fun favorites(): Flow<List<MediaItem>> =
        favoriteDao.observeAll().map { list -> list.map { it.toMediaItem() } }

    fun isFavorite(mediaId: String): Flow<Boolean> = favoriteDao.observeIsFavorite(mediaId)

    suspend fun toggleFavorite(item: MediaItem, makeFavorite: Boolean) {
        if (makeFavorite) {
            favoriteDao.add(
                FavoriteEntity(
                    mediaId = item.id,
                    type = item.type.name,
                    title = item.title,
                    posterUrl = item.posterUrl,
                    backdropUrl = item.backdropUrl,
                    year = item.year,
                    tmdbId = item.tmdbId,
                    imdbId = item.imdbId,
                    addedAt = clock(),
                ),
            )
        } else {
            favoriteDao.remove(item.id)
        }
    }

    fun continueWatching(): Flow<List<WatchProgressEntity>> = watchProgressDao.observeRecent()

    /** Saved resume position (ms) for a movie/episode id, or 0 if none. */
    suspend fun resumePosition(id: String): Long = watchProgressDao.get(id)?.positionMs ?: 0L

    suspend fun saveProgress(
        id: String,
        mediaId: String,
        type: MediaType,
        title: String,
        subtitle: String?,
        posterUrl: String?,
        backdropUrl: String?,
        positionMs: Long,
        durationMs: Long,
    ) {
        // Drop near-finished items so they don't clutter Continue Watching.
        if (durationMs > 0 && positionMs > durationMs * 0.95) {
            watchProgressDao.remove(id)
            return
        }
        watchProgressDao.upsert(
            WatchProgressEntity(
                id = id,
                mediaId = mediaId,
                type = type.name,
                title = title,
                subtitle = subtitle,
                posterUrl = posterUrl,
                backdropUrl = backdropUrl,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = clock(),
            ),
        )
    }

    private fun FavoriteEntity.toMediaItem() = MediaItem(
        id = mediaId,
        type = runCatching { MediaType.valueOf(type) }.getOrDefault(MediaType.MOVIE),
        title = title,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        year = year,
        tmdbId = tmdbId,
        imdbId = imdbId,
    )
}
