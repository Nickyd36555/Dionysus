package com.dionysus.tv.data.debrid

import com.dionysus.tv.core.model.DebridAccount
import com.dionysus.tv.core.model.DebridProvider
import com.dionysus.tv.core.model.ResolvedStream
import com.dionysus.tv.core.model.StreamSource

/**
 * A debrid provider that turns torrents/hoster links into directly playable
 * URLs. Implementations must be resilient: cache checks and resolution return
 * empty/null rather than throwing on transient failures.
 */
interface DebridService {
    val provider: DebridProvider

    suspend fun isConnected(): Boolean

    suspend fun account(): DebridAccount

    /**
     * Given a set of torrent info-hashes, return the subset (lowercased) that
     * the provider reports as already cached, so the UI can flag instant plays.
     */
    suspend fun checkCached(hashes: List<String>): Set<String>

    /**
     * Resolve a scraper [source] into a final playable URL, adding the torrent
     * to the provider if necessary. Returns null if it cannot be resolved.
     */
    suspend fun resolve(source: StreamSource): ResolvedStream?
}
