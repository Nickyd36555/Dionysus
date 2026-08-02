package com.dionysus.tv.data.debrid.premiumize

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** Premiumize REST API (base https://www.premiumize.me/api/). Auth via apikey. */
interface PremiumizeApi {

    @GET("account/info")
    suspend fun accountInfo(@Query("apikey") apiKey: String): PmAccountInfo

    @GET("cache/check")
    suspend fun cacheCheck(
        @Query("apikey") apiKey: String,
        @Query("items[]") hashes: List<String>,
    ): PmCacheCheck

    @FormUrlEncoded
    @POST("transfer/directdl")
    suspend fun directDownload(
        @Field("apikey") apiKey: String,
        @Field("src") src: String,
    ): PmDirectDownload

    companion object {
        const val BASE_URL = "https://www.premiumize.me/api/"
    }
}

@Serializable
data class PmAccountInfo(
    val status: String? = null,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("premium_until") val premiumUntil: Long? = null,
    @SerialName("limit_used") val limitUsed: Double? = null,
)

@Serializable
data class PmCacheCheck(
    val status: String? = null,
    val response: List<Boolean> = emptyList(),
    val filename: List<String?> = emptyList(),
    val filesize: List<Long?> = emptyList(),
)

@Serializable
data class PmDirectDownload(
    val status: String? = null,
    val error: String? = null,
    val message: String? = null,
    val location: String? = null,
    val filename: String? = null,
    val filesize: Long? = null,
    val content: List<PmContent> = emptyList(),
)

@Serializable
data class PmContent(
    val path: String = "",
    val size: Long = 0,
    val link: String? = null,
    @SerialName("stream_link") val streamLink: String? = null,
    @SerialName("transcode_status") val transcodeStatus: String? = null,
)
