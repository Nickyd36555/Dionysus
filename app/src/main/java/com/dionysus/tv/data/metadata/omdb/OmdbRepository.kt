package com.dionysus.tv.data.metadata.omdb

import com.dionysus.tv.core.model.MovieExtra
import com.dionysus.tv.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches supplemental IMDb-based info for a title from OMDb and maps it into the
 * app's [MovieExtra]. Best-effort: any failure (no key, network, not found)
 * simply yields null so detail pages fall back to TMDB-only data.
 */
@Singleton
class OmdbRepository @Inject constructor(
    private val api: OmdbApi,
    private val settings: SettingsRepository,
) {
    suspend fun extra(imdbId: String?, title: String, year: Int?): MovieExtra? {
        val key = settings.currentOmdbApiKey().ifBlank { return null }
        val resp = runCatching {
            if (!imdbId.isNullOrBlank()) {
                api.byImdbId(imdbId, key)
            } else if (title.isNotBlank()) {
                api.byTitle(title, year?.toString(), key)
            } else {
                null
            }
        }.getOrNull() ?: return null
        if (resp.response.equals("False", ignoreCase = true)) return null

        val extra = MovieExtra(
            rated = clean(resp.rated),
            director = clean(resp.director),
            writer = clean(resp.writer),
            cast = clean(resp.actors),
            awards = clean(resp.awards),
            boxOffice = clean(resp.boxOffice),
            imdbRating = clean(resp.imdbRating),
            rottenTomatoes = clean(resp.ratings.firstOrNull { it.source == "Rotten Tomatoes" }?.value),
            metacritic = clean(resp.ratings.firstOrNull { it.source == "Metacritic" }?.value),
            plot = clean(resp.plot),
        )
        return extra.takeIf { it != MovieExtra() }
    }

    /** OMDb uses the literal string "N/A" for missing values. */
    private fun clean(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() && !it.equals("N/A", ignoreCase = true) }
}
