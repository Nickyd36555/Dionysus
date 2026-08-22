package com.dionysus.tv.data.debrid

import com.dionysus.tv.core.model.DebridAccount
import com.dionysus.tv.core.model.DebridProvider
import com.dionysus.tv.core.model.ResolvedStream
import com.dionysus.tv.core.model.StreamSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates all configured debrid providers: annotates scraper results with
 * per-provider cache status and resolves a chosen source to a playable URL.
 */
@Singleton
class DebridRepository @Inject constructor(
    private val services: Set<@JvmSuppressWildcards DebridService>,
) {
    val allServices: List<DebridService> get() = services.toList()

    suspend fun accounts(): List<DebridAccount> = coroutineScope {
        services.map { async { it.account() } }.awaitAll()
    }

    private suspend fun connected(): List<DebridService> = coroutineScope {
        services.map { svc -> async { svc to svc.isConnected() } }
            .awaitAll()
            .filter { it.second }
            .map { it.first }
    }

    /** Flags each source with the providers that already have it cached. */
    suspend fun annotateCache(sources: List<StreamSource>): List<StreamSource> = coroutineScope {
        val hashes = sources.mapNotNull { it.infoHash?.lowercase() }.distinct()
        if (hashes.isEmpty()) return@coroutineScope sources

        val cacheByProvider: Map<DebridProvider, Set<String>> = connected()
            .map { svc -> async { svc.provider to svc.checkCached(hashes) } }
            .awaitAll()
            .toMap()

        sources.map { source ->
            val hash = source.infoHash?.lowercase() ?: return@map source
            val cachedOn = cacheByProvider
                .filterValues { hash in it }
                .keys
            if (cachedOn.isEmpty()) source else source.copy(cachedOn = cachedOn)
        }
    }

    /**
     * Resolve [source] to a playable URL. Tries the [preferred] provider first
     * (or the providers that report it cached), then any other connected one.
     */
    suspend fun resolve(source: StreamSource, preferred: DebridProvider? = null): ResolvedStream? {
        val connected = connected()
        if (connected.isEmpty()) return null

        val ordered = buildList {
            preferred?.let { p -> connected.firstOrNull { it.provider == p }?.let(::add) }
            addAll(connected.filter { it.provider in source.cachedOn })
            addAll(connected)
        }.distinct()

        // Remember an account-level error but keep trying other providers — only
        // surface it if none of them can serve the source.
        var accountError: DebridException? = null
        for (service in ordered) {
            try {
                service.resolve(source)?.let { return it }
            } catch (e: DebridException) {
                accountError = e
            }
        }
        accountError?.let { throw it }
        return null
    }
}
