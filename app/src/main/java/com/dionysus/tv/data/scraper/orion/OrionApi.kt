package com.dionysus.tv.data.scraper.orion

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Orion (orionoid.com) exposes a single JSON endpoint driven by query params.
 * We use mode=stream / action=retrieve to fetch indexed streams for a title.
 */
interface OrionApi {
    @GET(".")
    suspend fun retrieve(
        @Query("token") token: String,
        @Query("mode") mode: String = "stream",
        @Query("action") action: String = "retrieve",
        @Query("type") type: String,
        @Query("idimdb") idImdb: String? = null,
        @Query("query") query: String? = null,
        @Query("numberseason") season: Int? = null,
        @Query("numberepisode") episode: Int? = null,
        @Query("streamtype") streamType: String = "torrent",
        @Query("limitcount") limit: Int = 50,
        @Query("sortvalue") sortValue: String = "best",
    ): OrionResponse

    companion object {
        const val BASE_URL = "https://api.orionoid.com/"
    }
}

@Serializable
data class OrionResponse(
    val result: OrionResult? = null,
    val data: OrionData? = null,
)

@Serializable
data class OrionResult(
    val status: String? = null,
    val message: String? = null,
)

@Serializable
data class OrionData(
    val streams: List<OrionStream> = emptyList(),
)

@Serializable
data class OrionStream(
    val id: String? = null,
    val links: List<String> = emptyList(),
    val file: OrionFile? = null,
    val video: OrionVideo? = null,
    val stream: OrionStreamInfo? = null,
)

@Serializable
data class OrionFile(
    val name: String? = null,
    val hash: String? = null,
    val size: Long? = null,
)

@Serializable
data class OrionVideo(
    val quality: String? = null,
    val codec: String? = null,
)

@Serializable
data class OrionStreamInfo(
    val type: String? = null,
    val source: String? = null,
    val hoster: String? = null,
    @SerialName("seeds") val seeds: Int? = null,
)
