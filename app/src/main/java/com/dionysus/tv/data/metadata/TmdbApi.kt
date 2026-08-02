package com.dionysus.tv.data.metadata

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit binding for the TMDB v3 REST API. */
interface TmdbApi {

    @GET("trending/all/week")
    suspend fun trending(@Query("api_key") apiKey: String): TmdbPagedResponse

    @GET("movie/popular")
    suspend fun popularMovies(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1,
    ): TmdbPagedResponse

    @GET("tv/popular")
    suspend fun popularShows(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1,
    ): TmdbPagedResponse

    @GET("movie/top_rated")
    suspend fun topRatedMovies(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1,
    ): TmdbPagedResponse

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1,
    ): TmdbPagedResponse

    @GET("movie/{id}")
    suspend fun movieDetail(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
    ): TmdbMovieDetail

    @GET("tv/{id}")
    suspend fun tvDetail(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") append: String = "external_ids",
    ): TmdbTvDetail

    @GET("tv/{id}/season/{season}")
    suspend fun seasonDetail(
        @Path("id") id: Int,
        @Path("season") season: Int,
        @Query("api_key") apiKey: String,
    ): TmdbSeasonDetail

    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"
        const val IMAGE_BASE = "https://image.tmdb.org/t/p/"
        const val POSTER_SIZE = "w500"
        const val BACKDROP_SIZE = "w1280"
        const val STILL_SIZE = "w300"
    }
}
