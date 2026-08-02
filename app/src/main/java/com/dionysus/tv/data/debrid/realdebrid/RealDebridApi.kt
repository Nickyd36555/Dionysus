package com.dionysus.tv.data.debrid.realdebrid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Authenticated Real-Debrid REST API (base https://api.real-debrid.com/rest/1.0/). */
interface RealDebridApi {

    @GET("user")
    suspend fun user(): RdUser

    @FormUrlEncoded
    @POST("torrents/addMagnet")
    suspend fun addMagnet(@Field("magnet") magnet: String): RdAddResponse

    @FormUrlEncoded
    @POST("torrents/selectFiles/{id}")
    suspend fun selectFiles(
        @Path("id") id: String,
        @Field("files") files: String = "all",
    )

    @GET("torrents/info/{id}")
    suspend fun torrentInfo(@Path("id") id: String): RdTorrentInfo

    @FormUrlEncoded
    @POST("unrestrict/link")
    suspend fun unrestrict(@Field("link") link: String): RdUnrestrictedLink

    companion object {
        const val BASE_URL = "https://api.real-debrid.com/rest/1.0/"
    }
}

@Serializable
data class RdUser(
    val username: String? = null,
    val email: String? = null,
    val type: String? = null,
    val premium: Long? = null,
    val expiration: String? = null,
)

@Serializable
data class RdAddResponse(val id: String, val uri: String? = null)

@Serializable
data class RdTorrentInfo(
    val id: String,
    val filename: String? = null,
    val hash: String? = null,
    val status: String? = null,
    val progress: Double = 0.0,
    val files: List<RdFile> = emptyList(),
    val links: List<String> = emptyList(),
)

@Serializable
data class RdFile(
    val id: Int,
    val path: String = "",
    val bytes: Long = 0,
    val selected: Int = 0,
)

@Serializable
data class RdUnrestrictedLink(
    val id: String? = null,
    val filename: String? = null,
    val filesize: Long? = null,
    val link: String? = null,
    val download: String,
    @SerialName("mimeType") val mimeType: String? = null,
    val streamable: Int? = null,
)
