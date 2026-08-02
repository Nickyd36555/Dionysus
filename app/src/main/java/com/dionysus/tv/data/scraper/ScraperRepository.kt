package com.dionysus.tv.data.scraper

import com.dionysus.tv.core.model.StreamSource
import com.dionysus.tv.data.settings.SettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fans a [StreamQuery] out to every enabled, available [Scraper] concurrently,
 * then merges, de-duplicates (by info-hash), and ranks the results.
 */
@Singleton
class ScraperRepository @Inject constructor(
    private val scrapers: Set<@JvmSuppressWildcards Scraper>,
    private val settings: SettingsRepository,
) {
    /** All registered scrapers, for the Settings screen. */
    val allScrapers: List<Scraper> get() = scrapers.sortedBy { it.displayName }

    suspend fun findStreams(query: StreamQuery): List<StreamSource> = coroutineScope {
        val enabledIds = settings.enabledScraperIds.first()
        val active = scrapers.filter { it.id in enabledIds && it.isAvailable() }

        val results = active
            .map { scraper -> async { runCatching { scraper.findStreams(query) }.getOrDefault(emptyList()) } }
            .awaitAll()
            .flatten()

        results
            .dedupeByHash()
            .sortedWith(
                compareByDescending<StreamSource> { it.cachedOn.isNotEmpty() }
                    .thenByDescending { it.quality.rank }
                    .thenByDescending { it.seeders ?: 0 },
            )
    }

    private fun List<StreamSource>.dedupeByHash(): List<StreamSource> {
        val seen = HashMap<String, StreamSource>()
        val passthrough = ArrayList<StreamSource>()
        for (source in this) {
            val key = source.infoHash?.lowercase()
            if (key == null) {
                passthrough += source
            } else {
                // Prefer the entry that carries the most metadata / cache flags.
                val existing = seen[key]
                if (existing == null || source.cachedOn.size > existing.cachedOn.size) {
                    seen[key] = source
                }
            }
        }
        return seen.values + passthrough
    }
}
