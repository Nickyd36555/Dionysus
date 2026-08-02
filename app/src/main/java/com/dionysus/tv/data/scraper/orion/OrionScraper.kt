package com.dionysus.tv.data.scraper.orion

import android.util.Log
import com.dionysus.tv.core.model.MediaType
import com.dionysus.tv.core.model.Quality
import com.dionysus.tv.core.model.StreamSource
import com.dionysus.tv.data.scraper.Scraper
import com.dionysus.tv.data.scraper.StreamQuery
import com.dionysus.tv.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Scrapes indexed torrent streams from the Orion API (requires an API key). */
@Singleton
class OrionScraper @Inject constructor(
    private val api: OrionApi,
    private val settings: SettingsRepository,
) : Scraper {

    override val id = "orion"
    override val displayName = "Orion"

    override suspend fun isAvailable(): Boolean = settings.currentOrionApiKey() != null

    override suspend fun findStreams(query: StreamQuery): List<StreamSource> {
        val token = settings.currentOrionApiKey() ?: return emptyList()
        val type = if (query.type == MediaType.TV_SHOW) "show" else "movie"
        return try {
            val response = api.retrieve(
                token = token,
                type = type,
                idImdb = query.imdbId?.removePrefix("tt"),
                query = if (query.imdbId == null) query.title else null,
                season = query.season,
                episode = query.episode,
            )
            if (response.result?.status != "success") {
                Log.w(TAG, "Orion returned status=${response.result?.status}: ${response.result?.message}")
                return emptyList()
            }
            response.data?.streams.orEmpty().mapNotNull { it.toStreamSource() }
        } catch (t: Throwable) {
            Log.w(TAG, "Orion lookup failed", t)
            emptyList()
        }
    }

    private fun OrionStream.toStreamSource(): StreamSource? {
        val magnet = links.firstOrNull { it.startsWith("magnet:", ignoreCase = true) }
        val direct = links.firstOrNull { it.startsWith("http", ignoreCase = true) }
        val hash = file?.hash?.takeIf { it.isNotBlank() }
        if (magnet == null && direct == null && hash == null) return null
        val title = file?.name ?: stream?.source ?: "Orion stream"
        return StreamSource(
            title = title,
            provider = displayName,
            quality = mapQuality(video?.quality) ?: Quality.fromTitle(title),
            sizeBytes = file?.size,
            seeders = stream?.seeds,
            infoHash = hash,
            magnetUri = magnet,
            url = if (magnet == null && hash == null) direct else null,
        )
    }

    private fun mapQuality(orion: String?): Quality? = when (orion?.lowercase()) {
        "hd8k", "hd6k", "hd4k" -> Quality.UHD_4K
        "hd2k", "hd1080" -> Quality.FHD_1080
        "hd720" -> Quality.HD_720
        "sd", "scr", "cam" -> Quality.SD
        else -> null
    }

    companion object {
        private const val TAG = "OrionScraper"
    }
}
