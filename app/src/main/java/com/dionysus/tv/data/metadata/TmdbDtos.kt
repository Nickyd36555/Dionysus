package com.dionysus.tv.data.metadata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbPagedResponse(
    val page: Int = 1,
    val results: List<TmdbListItem> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
)

/** A row item from list/search endpoints. `media_type` present only on multi. */
@Serializable
data class TmdbListItem(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
)

@Serializable
data class TmdbGenre(val id: Int, val name: String)

@Serializable
data class TmdbMovieDetail(
    val id: Int,
    val title: String,
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    val runtime: Int? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    val genres: List<TmdbGenre> = emptyList(),
)

@Serializable
data class TmdbTvDetail(
    val id: Int,
    val name: String,
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int = 0,
    val genres: List<TmdbGenre> = emptyList(),
    val seasons: List<TmdbSeason> = emptyList(),
    @SerialName("external_ids") val externalIds: TmdbExternalIds? = null,
)

@Serializable
data class TmdbExternalIds(@SerialName("imdb_id") val imdbId: String? = null)

@Serializable
data class TmdbSeason(
    @SerialName("season_number") val seasonNumber: Int,
    val name: String = "",
    @SerialName("episode_count") val episodeCount: Int = 0,
    @SerialName("poster_path") val posterPath: String? = null,
)

@Serializable
data class TmdbSeasonDetail(
    @SerialName("season_number") val seasonNumber: Int,
    val episodes: List<TmdbEpisode> = emptyList(),
)

@Serializable
data class TmdbEpisode(
    @SerialName("episode_number") val episodeNumber: Int,
    @SerialName("season_number") val seasonNumber: Int,
    val name: String = "",
    val overview: String = "",
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    val runtime: Int? = null,
)
