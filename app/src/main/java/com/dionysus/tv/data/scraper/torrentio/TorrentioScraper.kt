package com.dionysus.tv.data.scraper.torrentio

import android.util.Log
import com.dionysus.tv.core.model.MediaType
import com.dionysus.tv.core.model.Quality
import com.dionysus.tv.core.model.StreamSource
import com.dionysus.tv.data.scraper.Scraper
import com.dionysus.tv.data.scraper.StreamQuery
import com.dionysus.tv.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scrapes torrent sources from a Torrentio (Stremio addon) instance. Requires
 * an IMDb id, which we get from TMDB's external ids.
 */
@Singleton
class TorrentioScraper @Inject constructor(
    private val api: TorrentioApi,
    private val settings: SettingsRepository,
) : Scraper {

    override val id = "torrentio"
    override val displayName = "Torrentio"

    override suspend fun isAvailable(): Boolean = true

    override suspend fun findStreams(query: StreamQuery): List<StreamSource> {
        val imdbId = query.imdbId ?: return emptyList()
        val raw = settings.torrentioBaseUrl.first()
        val base = if (raw.endsWith("/")) raw else "$raw/"
        val type = if (query.type == MediaType.TV_SHOW) "series" else "movie"
        val streamId = when {
            query.type == MediaType.TV_SHOW && query.season != null && query.episode != null ->
                "$imdbId:${query.season}:${query.episode}"
            else -> imdbId
        }
        val url = "${base}stream/$type/$streamId.json"

        return try {
            api.streams(url).streams.mapNotNull { it.toStreamSource() }
        } catch (t: Throwable) {
            Log.w(TAG, "Torrentio lookup failed for $streamId", t)
            emptyList()
        }
    }

    private fun TorrentioStream.toStreamSource(): StreamSource? {
        val hash = infoHash
        if (hash == null && url == null) return null
        val seeders = SEEDERS_REGEX.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val sizeBytes = behaviorHints?.videoSize ?: parseSize(title)
        val releaseTitle = title.lineSequence().firstOrNull()?.trim().orEmpty()
            .ifBlank { name.replace("\n", " ") }
        return StreamSource(
            title = releaseTitle,
            provider = displayName,
            quality = Quality.fromTitle("$name $title"),
            sizeBytes = sizeBytes,
            seeders = seeders,
            infoHash = hash,
            url = url,
            fileIndex = fileIdx,
        )
    }

    companion object {
        private const val TAG = "TorrentioScraper"
        private val SEEDERS_REGEX = Regex("""👤\s*(\d+)""")
        private val SIZE_REGEX = Regex("""💾\s*([\d.]+)\s*(GB|MB|TB)""", RegexOption.IGNORE_CASE)

        fun parseSize(text: String): Long? {
            val m = SIZE_REGEX.find(text) ?: return null
            val value = m.groupValues[1].toDoubleOrNull() ?: return null
            val multiplier = when (m.groupValues[2].uppercase()) {
                "TB" -> 1_099_511_627_776L
                "GB" -> 1_073_741_824L
                "MB" -> 1_048_576L
                else -> 1L
            }
            return (value * multiplier).toLong()
        }
    }
}
