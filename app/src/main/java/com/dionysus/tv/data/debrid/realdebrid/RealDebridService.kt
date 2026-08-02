package com.dionysus.tv.data.debrid.realdebrid

import android.util.Log
import com.dionysus.tv.core.model.DebridAccount
import com.dionysus.tv.core.model.DebridProvider
import com.dionysus.tv.core.model.ResolvedStream
import com.dionysus.tv.core.model.StreamSource
import com.dionysus.tv.data.debrid.DebridService
import com.dionysus.tv.data.debrid.VideoExtensions
import com.dionysus.tv.data.settings.SettingsRepository
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealDebridService @Inject constructor(
    private val api: RealDebridApi,
    private val settings: SettingsRepository,
) : DebridService {

    override val provider = DebridProvider.REAL_DEBRID

    override suspend fun isConnected(): Boolean = settings.currentRealDebridToken() != null

    override suspend fun account(): DebridAccount = try {
        val user = api.user()
        DebridAccount(
            provider = provider,
            isConnected = true,
            username = user.username,
            premiumExpiry = null,
        )
    } catch (t: Throwable) {
        DebridAccount(provider, isConnected = false)
    }

    /**
     * Real-Debrid removed the public instant-availability endpoint, so we no
     * longer pre-flag cached hashes here — resolution simply succeeds quickly
     * for cached torrents and times out for uncached ones.
     */
    override suspend fun checkCached(hashes: List<String>): Set<String> = emptySet()

    override suspend fun resolve(source: StreamSource): ResolvedStream? {
        if (!isConnected()) return null
        return try {
            // Directly playable hoster link — just unrestrict it.
            if (source.isDirect && source.url != null) {
                return unrestrict(source.url)
            }

            val magnet = source.magnetUri
                ?: source.infoHash?.let { "magnet:?xt=urn:btih:$it" }
                ?: return null

            val added = api.addMagnet(magnet)
            val info = api.torrentInfo(added.id)
            val fileId = pickFileId(info, source.fileIndex) ?: run {
                Log.w(TAG, "No suitable video file in torrent ${added.id}")
                return null
            }
            api.selectFiles(added.id, fileId.toString())

            val ready = pollUntilReady(added.id) ?: return null
            val link = ready.links.firstOrNull() ?: return null
            unrestrict(link)
        } catch (t: Throwable) {
            Log.w(TAG, "Real-Debrid resolve failed", t)
            null
        }
    }

    private suspend fun unrestrict(link: String): ResolvedStream {
        val u = api.unrestrict(link)
        return ResolvedStream(
            playbackUrl = u.download,
            fileName = u.filename ?: "video",
            mimeType = u.mimeType,
            sizeBytes = u.filesize,
            resolvedBy = provider,
        )
    }

    private fun pickFileId(info: RdTorrentInfo, torrentFileIndex: Int?): Int? {
        if (info.files.isEmpty()) return null
        torrentFileIndex?.let { idx ->
            info.files.getOrNull(idx)?.let { return it.id }
        }
        return info.files
            .filter { f -> VideoExtensions.matches(f.path) }
            .maxByOrNull { it.bytes }
            ?.id
            ?: info.files.maxByOrNull { it.bytes }?.id
    }

    private suspend fun pollUntilReady(id: String): RdTorrentInfo? {
        repeat(POLL_ATTEMPTS) {
            val info = api.torrentInfo(id)
            if (info.status == "downloaded" && info.links.isNotEmpty()) return info
            if (info.status in TERMINAL_FAILURES) return null
            delay(POLL_DELAY_MS)
        }
        return null
    }

    companion object {
        private const val TAG = "RealDebridService"
        private const val POLL_ATTEMPTS = 12
        private const val POLL_DELAY_MS = 1500L
        private val TERMINAL_FAILURES = setOf("error", "magnet_error", "virus", "dead")
    }
}
