package com.dionysus.tv.core.model

/** Whether a catalog entry is a film or an episodic series. */
enum class MediaType { MOVIE, TV_SHOW }

/**
 * A single browsable catalog entry (movie or show). Metadata comes from the
 * configured metadata provider (TMDB by default); playable streams are resolved
 * separately via [com.dionysus.tv.data.scraper.Scraper]s and debrid services.
 */
data class MediaItem(
    val id: String,
    val type: MediaType,
    val title: String,
    val overview: String = "",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val genres: List<String> = emptyList(),
    val runtimeMinutes: Int? = null,
    /** External ids used by scrapers to find matching streams. */
    val tmdbId: Int? = null,
    val imdbId: String? = null,
)

/** A season within a [MediaType.TV_SHOW]. */
data class Season(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int,
    val posterUrl: String? = null,
)

/** A single episode used both for browsing and for stream lookups. */
data class Episode(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val overview: String = "",
    val stillUrl: String? = null,
    val airDate: String? = null,
    val runtimeMinutes: Int? = null,
)
