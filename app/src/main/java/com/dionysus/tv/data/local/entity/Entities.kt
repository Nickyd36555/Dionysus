package com.dionysus.tv.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A title the user saved to "My List". */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val mediaId: String,
    val type: String,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: Int?,
    val tmdbId: Int?,
    val imdbId: String?,
    val addedAt: Long,
)

/** Resume point for "Continue Watching". Keyed per movie or per episode. */
@Entity(tableName = "watch_progress")
data class WatchProgressEntity(
    /** movieId, or "showId:s{n}e{n}" for episodes. */
    @PrimaryKey val id: String,
    val mediaId: String,
    val type: String,
    val title: String,
    val subtitle: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

/** A local download tracked through its lifecycle by [DownloadStatus]. */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val mediaId: String,
    val title: String,
    val sourceTitle: String,
    val quality: String,
    val remoteUrl: String,
    val localPath: String?,
    val posterUrl: String?,
    val status: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val createdAt: Long,
    val workId: String?,
)

/** A Stremio addon the user installed, stored with its raw manifest JSON. */
@Entity(tableName = "addons")
data class InstalledAddonEntity(
    @PrimaryKey val transportUrl: String,
    val name: String,
    val manifestJson: String,
    val position: Int,
    val installedAt: Long,
)

/**
 * One configurable row on the home screen. The user can reorder, rename,
 * toggle, and add rows; this table is the source of truth for the layout.
 */
@Entity(tableName = "home_rows")
data class HomeRowConfigEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val position: Int,
    val enabled: Boolean,
    /** For CUSTOM_GENRE rows: the TMDB genre id or search term. */
    val param: String?,
)
