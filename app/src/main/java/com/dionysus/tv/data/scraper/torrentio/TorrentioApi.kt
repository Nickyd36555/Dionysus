package com.dionysus.tv.data.scraper.torrentio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url

/**
 * Torrentio is a Stremio addon. Its stream endpoint is:
 *   {base}/stream/{type}/{id}.json
 * where type is "movie" or "series" and id is an IMDb id, optionally suffixed
 * with ":season:episode" for series.
 */
interface TorrentioApi {
    @GET
    suspend fun streams(@Url url: String): TorrentioResponse
}

@Serializable
data class TorrentioResponse(
    val streams: List<TorrentioStream> = emptyList(),
)

@Serializable
data class TorrentioStream(
    /** Provider + quality summary, e.g. "Torrentio\n1080p". */
    val name: String = "",
    /** Multi-line release title with seeders/size/source glyphs. */
    val title: String = "",
    val infoHash: String? = null,
    @SerialName("fileIdx") val fileIdx: Int? = null,
    /** Present when Torrentio is configured with a debrid token. */
    val url: String? = null,
    val behaviorHints: TorrentioBehaviorHints? = null,
)

@Serializable
data class TorrentioBehaviorHints(
    val bingeGroup: String? = null,
    val filename: String? = null,
    val videoSize: Long? = null,
)
