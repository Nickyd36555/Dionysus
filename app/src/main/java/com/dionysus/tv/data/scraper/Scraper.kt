package com.dionysus.tv.data.scraper

import com.dionysus.tv.core.model.MediaType
import com.dionysus.tv.core.model.StreamSource

/**
 * What to look up. For episodes, [season] and [episode] are set and [imdbId] is
 * used to build the `imdb:season:episode` key that most scrapers expect.
 */
data class StreamQuery(
    val type: MediaType,
    val title: String,
    val year: Int? = null,
    val imdbId: String? = null,
    val tmdbId: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
)

/**
 * A source of candidate [StreamSource]s. New providers (Jackett, Prowlarr,
 * other Stremio addons, ...) only need to implement this interface and be added
 * to the DI set — nothing else in the app changes.
 */
interface Scraper {
    /** Stable identifier persisted in settings (e.g. "torrentio"). */
    val id: String

    /** Human-readable name shown in Settings. */
    val displayName: String

    /** True when this scraper has everything it needs (keys, etc.) to run. */
    suspend fun isAvailable(): Boolean

    /** Returns candidate streams, or an empty list on failure — never throws. */
    suspend fun findStreams(query: StreamQuery): List<StreamSource>
}
