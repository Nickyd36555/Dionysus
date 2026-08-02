package com.dionysus.tv.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Minimal GitHub REST binding used for self-update: Dionysus is sideloaded
 * (not on the Play Store), so it checks its own repo's latest release for a
 * newer versioned APK asset.
 */
interface GitHubApi {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun latestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): GhRelease

    companion object {
        const val BASE_URL = "https://api.github.com/"
    }
}

@Serializable
data class GhRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String = "",
    val prerelease: Boolean = false,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
data class GhAsset(
    val name: String = "",
    val size: Long = 0,
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)
