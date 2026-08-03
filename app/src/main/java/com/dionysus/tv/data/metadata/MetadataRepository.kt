package com.dionysus.tv.data.metadata

import com.dionysus.tv.core.model.DataResult
import com.dionysus.tv.core.model.Episode
import com.dionysus.tv.core.model.MediaItem
import com.dionysus.tv.core.model.MediaType
import com.dionysus.tv.core.model.Season
import com.dionysus.tv.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns raw TMDB responses into the app's domain [MediaItem]s. All calls fail
 * fast with a friendly message when no TMDB key has been configured in Settings.
 */
@Singleton
class MetadataRepository @Inject constructor(
    private val api: TmdbApi,
    private val settings: SettingsRepository,
) {
    private suspend fun requireKey(): String =
        settings.currentTmdbApiKey()
            ?: throw IllegalStateException("Add a TMDB API key in Settings → Metadata to browse the catalog.")

    suspend fun trending(): DataResult<List<MediaItem>> = DataResult.catching {
        api.trending(requireKey()).results.mapNotNull { it.toMediaItem() }
    }

    suspend fun popularMovies(): DataResult<List<MediaItem>> = DataResult.catching {
        api.popularMovies(requireKey()).results.map { it.toMediaItem(MediaType.MOVIE) }
    }

    suspend fun popularShows(): DataResult<List<MediaItem>> = DataResult.catching {
        api.popularShows(requireKey()).results.map { it.toMediaItem(MediaType.TV_SHOW) }
    }

    suspend fun topRatedMovies(): DataResult<List<MediaItem>> = DataResult.catching {
        api.topRatedMovies(requireKey()).results.map { it.toMediaItem(MediaType.MOVIE) }
    }

    suspend fun search(query: String): DataResult<List<MediaItem>> = DataResult.catching {
        api.searchMulti(requireKey(), query).results
            // multi-search also returns people/actors, which aren't playable.
            .filter { it.mediaType == "movie" || it.mediaType == "tv" }
            .map { it.toMediaItem() }
    }

    suspend fun movieDetail(id: Int): DataResult<MediaItem> = DataResult.catching {
        api.movieDetail(id, requireKey()).toMediaItem()
    }

    suspend fun tvDetail(id: Int): DataResult<Pair<MediaItem, List<Season>>> = DataResult.catching {
        val detail = api.tvDetail(id, requireKey())
        detail.toMediaItem() to detail.seasons
            .filter { it.seasonNumber > 0 }
            .map { Season(it.seasonNumber, it.name, it.episodeCount, it.posterPath.toImageUrl(TmdbApi.POSTER_SIZE)) }
    }

    suspend fun episodes(tvId: Int, season: Int): DataResult<List<Episode>> = DataResult.catching {
        api.seasonDetail(tvId, season, requireKey()).episodes.map {
            Episode(
                seasonNumber = it.seasonNumber,
                episodeNumber = it.episodeNumber,
                title = it.name,
                overview = it.overview,
                stillUrl = it.stillPath.toImageUrl(TmdbApi.STILL_SIZE),
                airDate = it.airDate,
                runtimeMinutes = it.runtime,
            )
        }
    }

    // ---- Mappers -------------------------------------------------------------

    private fun TmdbListItem.toMediaItem(forced: MediaType? = null): MediaItem {
        val type = forced ?: when (mediaType) {
            "tv" -> MediaType.TV_SHOW
            else -> MediaType.MOVIE
        }
        val displayTitle = title ?: name ?: "Untitled"
        val date = releaseDate ?: firstAirDate
        return MediaItem(
            id = "tmdb:${if (type == MediaType.TV_SHOW) "tv" else "movie"}:$id",
            type = type,
            title = displayTitle,
            overview = overview,
            posterUrl = posterPath.toImageUrl(TmdbApi.POSTER_SIZE),
            backdropUrl = backdropPath.toImageUrl(TmdbApi.BACKDROP_SIZE),
            year = date.toYear(),
            rating = voteAverage,
            tmdbId = id,
        )
    }

    private fun TmdbMovieDetail.toMediaItem() = MediaItem(
        id = "tmdb:movie:$id",
        type = MediaType.MOVIE,
        title = title,
        overview = overview,
        posterUrl = posterPath.toImageUrl(TmdbApi.POSTER_SIZE),
        backdropUrl = backdropPath.toImageUrl(TmdbApi.BACKDROP_SIZE),
        year = releaseDate.toYear(),
        rating = voteAverage,
        runtimeMinutes = runtime,
        genres = genres.map { it.name },
        tmdbId = id,
        imdbId = imdbId,
    )

    private fun TmdbTvDetail.toMediaItem() = MediaItem(
        id = "tmdb:tv:$id",
        type = MediaType.TV_SHOW,
        title = name,
        overview = overview,
        posterUrl = posterPath.toImageUrl(TmdbApi.POSTER_SIZE),
        backdropUrl = backdropPath.toImageUrl(TmdbApi.BACKDROP_SIZE),
        year = firstAirDate.toYear(),
        rating = voteAverage,
        genres = genres.map { it.name },
        tmdbId = id,
        imdbId = externalIds?.imdbId,
    )

    private fun String?.toImageUrl(size: String): String? =
        this?.let { "${TmdbApi.IMAGE_BASE}$size$it" }

    private fun String?.toYear(): Int? =
        this?.take(4)?.toIntOrNull()
}
