package com.dionysus.tv.data.debrid.realdebrid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Unauthenticated Real-Debrid OAuth2 device-flow API. The device flow is ideal
 * for TVs: the user enters a short code on their phone/computer, no keyboard
 * juggling on the remote.
 */
interface RealDebridAuthApi {

    /** Step 1: obtain a device + user code. */
    @GET("device/code")
    suspend fun deviceCode(
        @Query("client_id") clientId: String = OPEN_SOURCE_CLIENT_ID,
        @Query("new_credentials") newCredentials: String = "yes",
    ): RdDeviceCode

    /** Step 2 (polled): once the user authorizes, returns app client credentials. */
    @GET("device/credentials")
    suspend fun credentials(
        @Query("client_id") clientId: String = OPEN_SOURCE_CLIENT_ID,
        @Query("code") deviceCode: String,
    ): RdClientCredentials

    /** Step 3: exchange the device code for access + refresh tokens. */
    @FormUrlEncoded
    @POST("token")
    suspend fun token(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String,
        @Field("grant_type") grantType: String = DEVICE_GRANT_TYPE,
    ): RdToken

    companion object {
        const val BASE_URL = "https://api.real-debrid.com/oauth/v2/"
        // Well-known open-source client id published by Real-Debrid.
        const val OPEN_SOURCE_CLIENT_ID = "X245A4XAIBGVM"
        const val DEVICE_GRANT_TYPE = "http://oauth.net/grant_type/device/1.0"
    }
}

@Serializable
data class RdDeviceCode(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_url") val verificationUrl: String,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int,
)

@Serializable
data class RdClientCredentials(
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,
)

@Serializable
data class RdToken(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Int = 3600,
    @SerialName("token_type") val tokenType: String = "Bearer",
)
