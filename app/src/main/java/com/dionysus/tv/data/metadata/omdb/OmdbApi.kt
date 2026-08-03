package com.dionysus.tv.data.metadata.omdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * OMDb (Open Movie Database) — supplements TMDB with IMDb/Rotten Tomatoes/
 * Metacritic ratings, cast, director, awards, and box-office figures. Looked up
 * by IMDb id when we have one, otherwise by title + year.
 */
interface OmdbApi {

    @GET(".")
    suspend fun byImdbId(
        @Query("i") imdbId: String,
        @Query("apikey") apiKey: String,
        @Query("plot") plot: String = "full",
    ): OmdbResponse

    @GET(".")
    suspend fun byTitle(
        @Query("t") title: String,
        @Query("y") year: String?,
        @Query("apikey") apiKey: String,
        @Query("plot") plot: String = "full",
    ): OmdbResponse

    companion object {
        const val BASE_URL = "https://www.omdbapi.com/"
    }
}

@Serializable
data class OmdbResponse(
    @SerialName("Response") val response: String? = null,
    @SerialName("Rated") val rated: String? = null,
    @SerialName("Director") val director: String? = null,
    @SerialName("Writer") val writer: String? = null,
    @SerialName("Actors") val actors: String? = null,
    @SerialName("Awards") val awards: String? = null,
    @SerialName("BoxOffice") val boxOffice: String? = null,
    @SerialName("Production") val production: String? = null,
    @SerialName("imdbRating") val imdbRating: String? = null,
    @SerialName("imdbVotes") val imdbVotes: String? = null,
    @SerialName("Metascore") val metascore: String? = null,
    @SerialName("Plot") val plot: String? = null,
    @SerialName("Ratings") val ratings: List<OmdbRating> = emptyList(),
)

@Serializable
data class OmdbRating(
    @SerialName("Source") val source: String? = null,
    @SerialName("Value") val value: String? = null,
)
