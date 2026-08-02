package com.dionysus.tv.data.debrid.premiumize

import android.util.Log
import com.dionysus.tv.core.model.DebridAccount
import com.dionysus.tv.core.model.DebridProvider
import com.dionysus.tv.core.model.ResolvedStream
import com.dionysus.tv.core.model.StreamSource
import com.dionysus.tv.data.debrid.DebridService
import com.dionysus.tv.data.debrid.VideoExtensions
import com.dionysus.tv.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PremiumizeService @Inject constructor(
    private val api: PremiumizeApi,
    private val settings: SettingsRepository,
) : DebridService {

    override val provider = DebridProvider.PREMIUMIZE

    override suspend fun isConnected(): Boolean = settings.currentPremiumizeApiKey() != null

    override suspend fun account(): DebridAccount {
        val key = settings.currentPremiumizeApiKey() ?: return DebridAccount(provider, false)
        return try {
            val info = api.accountInfo(key)
            DebridAccount(
                provider = provider,
                isConnected = info.status == "success",
                username = info.customerId,
                premiumExpiry = info.premiumUntil,
            )
        } catch (t: Throwable) {
            DebridAccount(provider, isConnected = false)
        }
    }

    override suspend fun checkCached(hashes: List<String>): Set<String> {
        val key = settings.currentPremiumizeApiKey() ?: return emptySet()
        if (hashes.isEmpty()) return emptySet()
        return try {
            val result = api.cacheCheck(key, hashes)
            hashes.filterIndexed { index, _ -> result.response.getOrNull(index) == true }
                .map { it.lowercase() }
                .toSet()
        } catch (t: Throwable) {
            Log.w(TAG, "Premiumize cache check failed", t)
            emptySet()
        }
    }

    override suspend fun resolve(source: StreamSource): ResolvedStream? {
        val key = settings.currentPremiumizeApiKey() ?: return null
        // directdl needs a magnet URI or a hoster URL — a bare info-hash is
        // rejected, so synthesize a magnet from the hash when that's all we have.
        val src = source.magnetUri
            ?: source.infoHash?.let { "magnet:?xt=urn:btih:$it" }
            ?: source.url
            ?: return null
        return try {
            val dd = api.directDownload(key, src)
            if (dd.status != "success") {
                Log.w(TAG, "Premiumize directdl error: ${dd.error ?: dd.message}")
                return null
            }
            val file = dd.content
                .filter { VideoExtensions.matches(it.path) && !VideoExtensions.isSample(it.path) }
                .maxByOrNull { it.size }
                ?: dd.content.maxByOrNull { it.size }

            val url = file?.link ?: dd.location ?: return null
            ResolvedStream(
                playbackUrl = url,
                fileName = file?.path?.substringAfterLast('/') ?: dd.filename ?: "video",
                sizeBytes = file?.size ?: dd.filesize,
                resolvedBy = provider,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Premiumize resolve failed", t)
            null
        }
    }

    companion object {
        private const val TAG = "PremiumizeService"
    }
}
