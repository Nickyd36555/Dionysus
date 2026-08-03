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

/**
 * Extra detail-page info sourced from OMDb (IMDb data). All optional — the UI
 * only renders the fields that are present.
 */
data class MovieExtra(
    val rated: String? = null,
    val director: String? = null,
    val writer: String? = null,
    val cast: String? = null,
    val awards: String? = null,
    val boxOffice: String? = null,
    val imdbRating: String? = null,
    val rottenTomatoes: String? = null,
    val metacritic: String? = null,
    val plot: String? = null,
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
