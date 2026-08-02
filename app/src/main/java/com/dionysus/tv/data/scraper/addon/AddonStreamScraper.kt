package com.dionysus.tv.data.scraper.addon

import android.util.Log
import com.dionysus.tv.core.model.MediaType
import com.dionysus.tv.core.model.Quality
import com.dionysus.tv.core.model.StreamSource
import com.dionysus.tv.data.addons.AddonApi
import com.dionysus.tv.data.addons.AddonRepository
import com.dionysus.tv.data.addons.AddonStream
import com.dionysus.tv.data.scraper.Scraper
import com.dionysus.tv.data.scraper.StreamQuery
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A single [Scraper] that fans a query out to every installed Stremio addon
 * that provides streams. This is how user-added addons contribute sources —
 * the same mechanism Torrentio uses, generalized to any addon.
 */
@Singleton
class AddonStreamScraper @Inject constructor(
    private val addons: AddonRepository,
    private val api: AddonApi,
) : Scraper {

    override val id = "stremio_addons"
    override val displayName = "Stremio Add-ons"

    override suspend fun isAvailable(): Boolean =
        addons.current().any { it.providesStream }

    override suspend fun findStreams(query: StreamQuery): List<StreamSource> = coroutineScope {
        val contentId = query.stremioId ?: query.imdbId ?: return@coroutineScope emptyList()
        val type = if (query.type == MediaType.TV_SHOW) "series" else "movie"
        val streamId = if (query.type == MediaType.TV_SHOW && query.season != null && query.episode != null) {
            "$contentId:${query.season}:${query.episode}"
        } else {
            contentId
        }

        val streamAddons = addons.current().filter { it.providesStream }
        streamAddons
            .map { addon ->
                async {
                    runCatching {
                        api.streams("${addon.base}stream/$type/$streamId.json").streams
                            .mapNotNull { it.toStreamSource(addon.manifest.name) }
                    }.getOrElse {
                        Log.w(TAG, "Stream fetch failed for ${addon.manifest.name}", it)
                        emptyList()
                    }
                }
            }
            .awaitAll()
            .flatten()
    }

    private fun AddonStream.toStreamSource(provider: String): StreamSource? {
        val hash = infoHash
        if (hash == null && url == null) return null
        val text = "$name $title $description"
        val seeders = SEEDERS_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val releaseTitle = title.lineSequence().firstOrNull()?.trim().orEmpty()
            .ifBlank { name.replace("\n", " ") }
        return StreamSource(
            title = releaseTitle.ifBlank { "Stream" },
            provider = provider,
            quality = Quality.fromTitle(text),
            sizeBytes = behaviorHints?.videoSize ?: parseSize(text),
            seeders = seeders,
            infoHash = hash,
            url = url,
            fileIndex = fileIdx,
        )
    }

    companion object {
        private const val TAG = "AddonStreamScraper"
        private val SEEDERS_REGEX = Regex("""👤\s*(\d+)""")
        private val SIZE_REGEX = Regex("""💾\s*([\d.]+)\s*(GB|MB|TB)""", RegexOption.IGNORE_CASE)

        private fun parseSize(text: String): Long? {
            val m = SIZE_REGEX.find(text) ?: return null
            val value = m.groupValues[1].toDoubleOrNull() ?: return null
            val mult = when (m.groupValues[2].uppercase()) {
                "TB" -> 1_099_511_627_776L
                "GB" -> 1_073_741_824L
                "MB" -> 1_048_576L
                else -> 1L
            }
            return (value * mult).toLong()
        }
    }
}
