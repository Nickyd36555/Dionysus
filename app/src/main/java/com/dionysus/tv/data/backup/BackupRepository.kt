package com.dionysus.tv.data.backup

import android.util.Base64
import com.dionysus.tv.core.model.DataResult
import com.dionysus.tv.data.addons.AddonRepository
import com.dionysus.tv.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A portable snapshot of every connected API + preference. Serialized to a
 * base64 "sync code" so a user can move their whole setup to another device
 * without re-entering anything — effectively logging in their connections.
 */
@Serializable
data class BackupData(
    val version: Int = 1,
    val realDebridToken: String? = null,
    val realDebridRefresh: String? = null,
    val realDebridClientId: String? = null,
    val realDebridClientSecret: String? = null,
    val premiumizeKey: String? = null,
    val orionKey: String? = null,
    val tmdbKey: String? = null,
    val torrentioUrl: String? = null,
    val preferredPlayer: String? = null,
    val onlyCached: Boolean = false,
    val enabledScrapers: List<String> = emptyList(),
    val addonUrls: List<String> = emptyList(),
)

@Singleton
class BackupRepository @Inject constructor(
    private val settings: SettingsRepository,
    private val addons: AddonRepository,
    private val json: Json,
) {
    /** Produce a shareable code containing the whole current setup. */
    suspend fun export(): String {
        val data = BackupData(
            realDebridToken = settings.currentRealDebridToken(),
            realDebridRefresh = settings.currentRealDebridRefresh(),
            realDebridClientId = settings.currentRealDebridClientId(),
            realDebridClientSecret = settings.currentRealDebridClientSecret(),
            premiumizeKey = settings.currentPremiumizeApiKey(),
            orionKey = settings.currentOrionApiKey(),
            tmdbKey = settings.currentTmdbApiKey(),
            torrentioUrl = settings.torrentioBaseUrl.first(),
            preferredPlayer = settings.preferredPlayerId.first(),
            onlyCached = settings.currentOnlyCached(),
            enabledScrapers = settings.enabledScraperIds.first().toList(),
            addonUrls = addons.current().map { it.transportUrl },
        )
        val encoded = json.encodeToString(BackupData.serializer(), data)
        return Base64.encodeToString(encoded.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    /** Apply a code produced by [export], restoring all connections. */
    suspend fun import(code: String): DataResult<Int> = DataResult.catching {
        val bytes = Base64.decode(code.trim(), Base64.DEFAULT)
        val data = json.decodeFromString(BackupData.serializer(), String(bytes, Charsets.UTF_8))

        val rd = data.realDebridToken
        if (!rd.isNullOrBlank() &&
            !data.realDebridRefresh.isNullOrBlank() &&
            !data.realDebridClientId.isNullOrBlank() &&
            !data.realDebridClientSecret.isNullOrBlank()
        ) {
            settings.setRealDebridOAuth(rd, data.realDebridRefresh, data.realDebridClientId, data.realDebridClientSecret)
        } else if (!rd.isNullOrBlank()) {
            settings.setRealDebridToken(rd)
        }

        settings.setPremiumizeApiKey(data.premiumizeKey)
        settings.setOrionApiKey(data.orionKey)
        settings.setTmdbApiKey(data.tmdbKey)
        data.torrentioUrl?.let { settings.setTorrentioBaseUrl(it) }
        data.preferredPlayer?.let { settings.setPreferredPlayer(it) }
        settings.setOnlyCached(data.onlyCached)
        data.enabledScrapers.forEach { settings.setScraperEnabled(it, true) }

        var addonCount = 0
        data.addonUrls.forEach { url ->
            if (addons.install(url) is DataResult.Success) addonCount++
        }
        addonCount
    }
}
