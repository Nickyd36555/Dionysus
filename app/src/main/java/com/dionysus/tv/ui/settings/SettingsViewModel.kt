package com.dionysus.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dionysus.tv.core.model.DataResult
import com.dionysus.tv.core.model.DebridProvider
import com.dionysus.tv.core.model.HomeRow
import com.dionysus.tv.data.addons.Addon
import com.dionysus.tv.data.addons.AddonRepository
import com.dionysus.tv.data.backup.BackupRepository
import com.dionysus.tv.data.debrid.DebridRepository
import com.dionysus.tv.data.debrid.realdebrid.RealDebridAuth
import com.dionysus.tv.data.debrid.realdebrid.RdDeviceCode
import com.dionysus.tv.data.iptv.IptvRepository
import com.dionysus.tv.data.iptv.StoredPlaylist
import com.dionysus.tv.data.local.HomeLayoutRepository
import com.dionysus.tv.data.scraper.ScraperRepository
import com.dionysus.tv.data.settings.SettingsRepository
import com.dionysus.tv.player.ExternalPlayer
import com.dionysus.tv.player.PlayerLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScraperInfo(val id: String, val name: String, val enabled: Boolean)

data class SettingsUiState(
    val tmdbKey: String = "",
    val omdbKey: String = "",
    val premiumizeKey: String = "",
    val orionKey: String = "",
    val torrentioUrl: String = "",
    val preferredPlayer: String = ExternalPlayer.INTERNAL.id,
    val availablePlayers: List<ExternalPlayer> = listOf(ExternalPlayer.INTERNAL),
    val realDebridConnected: Boolean = false,
    val realDebridUser: String? = null,
    val premiumizeConnected: Boolean = false,
    val onlyCached: Boolean = false,
    val deviceCode: RdDeviceCode? = null,
    val statusMessage: String? = null,
    val scrapers: List<ScraperInfo> = emptyList(),
    val homeRows: List<HomeRow> = emptyList(),
    val featuredSource: String = "TRENDING",
    val downloadFolder: String? = null,
    val addons: List<Addon> = emptyList(),
    val addonUrlInput: String = "",
    val syncCode: String? = null,
    val importCodeInput: String = "",
    // Live TV (IPTV)
    val playlists: List<StoredPlaylist> = emptyList(),
    val m3uName: String = "",
    val m3uUrl: String = "",
    val m3uEpgUrl: String = "",
    val xtreamName: String = "",
    val xtreamHost: String = "",
    val xtreamUser: String = "",
    val xtreamPass: String = "",
    val xtreamEpg: String = "",
    val editingPlaylistId: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val realDebridAuth: RealDebridAuth,
    private val debrid: DebridRepository,
    private val scraperRepository: ScraperRepository,
    private val homeLayout: HomeLayoutRepository,
    private val playerLauncher: PlayerLauncher,
    private val addonRepository: AddonRepository,
    private val backup: BackupRepository,
    private val iptv: IptvRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { loadInitial() }
        homeLayout.rows
            .onEach { rows -> _state.value = _state.value.copy(homeRows = rows) }
            .launchIn(viewModelScope)
        addonRepository.installedAddons
            .onEach { list -> _state.value = _state.value.copy(addons = list) }
            .launchIn(viewModelScope)
        iptv.playlists
            .onEach { list -> _state.value = _state.value.copy(playlists = list) }
            .launchIn(viewModelScope)
    }

    // ---- Live TV (IPTV) ---------------------------------------------------
    fun setM3uName(v: String) { _state.value = _state.value.copy(m3uName = v) }
    fun setM3uUrl(v: String) { _state.value = _state.value.copy(m3uUrl = v) }
    fun setM3uEpgUrl(v: String) { _state.value = _state.value.copy(m3uEpgUrl = v) }
    fun setXtreamName(v: String) { _state.value = _state.value.copy(xtreamName = v) }
    fun setXtreamHost(v: String) { _state.value = _state.value.copy(xtreamHost = v) }
    fun setXtreamUser(v: String) { _state.value = _state.value.copy(xtreamUser = v) }
    fun setXtreamPass(v: String) { _state.value = _state.value.copy(xtreamPass = v) }
    fun setXtreamEpg(v: String) { _state.value = _state.value.copy(xtreamEpg = v) }

    fun addM3uPlaylist() {
        val s = _state.value
        if (s.m3uUrl.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(statusMessage = if (s.editingPlaylistId != null) "Saving playlist…" else "Adding playlist…")
            when (val r = iptv.addM3u(s.m3uName, s.m3uUrl, s.m3uEpgUrl)) {
                is DataResult.Success -> {
                    s.editingPlaylistId?.let { iptv.remove(it) }
                    _state.value = _state.value.copy(
                        m3uName = "", m3uUrl = "", m3uEpgUrl = "", editingPlaylistId = null,
                        statusMessage = "Playlist saved.",
                    )
                }
                is DataResult.Error -> _state.value = _state.value.copy(
                    statusMessage = "Couldn't save playlist: ${r.message}",
                )
            }
        }
    }

    fun addXtreamPlaylist() {
        val s = _state.value
        if (s.xtreamHost.isBlank() || s.xtreamUser.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(statusMessage = "Connecting to Xtream server…")
            when (val r = iptv.addXtream(s.xtreamName, s.xtreamHost, s.xtreamUser, s.xtreamPass, s.xtreamEpg)) {
                is DataResult.Success -> {
                    s.editingPlaylistId?.let { iptv.remove(it) }
                    _state.value = _state.value.copy(
                        xtreamName = "", xtreamHost = "", xtreamUser = "", xtreamPass = "", xtreamEpg = "", editingPlaylistId = null,
                        statusMessage = "Xtream account saved.",
                    )
                }
                is DataResult.Error -> _state.value = _state.value.copy(
                    statusMessage = "Couldn't save Xtream account: ${r.message}",
                )
            }
        }
    }

    /** Load a stored playlist into the matching input fields for editing. */
    fun editPlaylist(playlist: StoredPlaylist) {
        _state.value = if (playlist.kind == "xtream") {
            _state.value.copy(
                editingPlaylistId = playlist.id,
                xtreamName = playlist.name,
                xtreamHost = playlist.host,
                xtreamUser = playlist.username,
                xtreamPass = playlist.password,
                xtreamEpg = playlist.epgUrl,
                statusMessage = "Editing \"${playlist.name}\" — change fields and press Add Xtream account to save.",
            )
        } else {
            _state.value.copy(
                editingPlaylistId = playlist.id,
                m3uName = playlist.name,
                m3uUrl = playlist.url,
                m3uEpgUrl = playlist.epgUrl,
                statusMessage = "Editing \"${playlist.name}\" — change fields and press Add M3U playlist to save.",
            )
        }
    }

    fun removePlaylist(id: String) {
        viewModelScope.launch {
            iptv.remove(id)
            _state.value = _state.value.copy(statusMessage = "Playlist removed.")
        }
    }

    fun setAddonUrl(url: String) {
        _state.value = _state.value.copy(addonUrlInput = url)
    }

    fun installAddon() {
        val url = _state.value.addonUrlInput.trim()
        if (url.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(statusMessage = "Installing add-on…")
            when (val result = addonRepository.install(url)) {
                is DataResult.Success -> _state.value = _state.value.copy(
                    addonUrlInput = "",
                    statusMessage = "Added \"${result.data.manifest.name}\".",
                )
                is DataResult.Error -> _state.value = _state.value.copy(
                    statusMessage = "Couldn't add add-on: ${result.message}",
                )
            }
        }
    }

    fun removeAddon(transportUrl: String) {
        viewModelScope.launch {
            addonRepository.remove(transportUrl)
            _state.value = _state.value.copy(statusMessage = "Add-on removed.")
        }
    }

    fun generateSyncCode() {
        viewModelScope.launch {
            val code = backup.export()
            _state.value = _state.value.copy(
                syncCode = code,
                statusMessage = "Sync code ready — copy it onto your other device and paste it under Restore.",
            )
        }
    }

    fun setImportCode(code: String) {
        _state.value = _state.value.copy(importCodeInput = code)
    }

    fun restoreFromCode() {
        val code = _state.value.importCodeInput.trim()
        if (code.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(statusMessage = "Restoring setup…")
            when (val result = backup.import(code)) {
                is DataResult.Success -> {
                    loadInitial()
                    _state.value = _state.value.copy(
                        importCodeInput = "",
                        statusMessage = "Setup restored (${result.data} add-ons). Your connections are ready.",
                    )
                }
                is DataResult.Error -> _state.value = _state.value.copy(
                    statusMessage = "Couldn't restore: ${result.message}",
                )
            }
        }
    }

    /** Load an addon's URL into the input so it can be edited and re-installed. */
    fun editAddon(transportUrl: String) {
        _state.value = _state.value.copy(
            addonUrlInput = transportUrl,
            statusMessage = "Loaded URL above — edit it and press Install (then Remove the old one if the URL changed).",
        )
    }

    private suspend fun loadInitial() {
        val enabled = settings.enabledScraperIds.first()
        _state.value = _state.value.copy(
            tmdbKey = settings.currentTmdbApiKey().orEmpty(),
            omdbKey = settings.currentOmdbApiKey(),
            featuredSource = settings.currentFeaturedSource(),
            downloadFolder = settings.currentDownloadFolderUri(),
            premiumizeKey = settings.currentPremiumizeApiKey().orEmpty(),
            orionKey = settings.currentOrionApiKey().orEmpty(),
            torrentioUrl = settings.torrentioBaseUrl.first(),
            preferredPlayer = settings.preferredPlayerId.first(),
            onlyCached = settings.currentOnlyCached(),
            availablePlayers = listOf(ExternalPlayer.INTERNAL) + playerLauncher.installedExternalPlayers(),
            scrapers = scraperRepository.allScrapers.map {
                ScraperInfo(it.id, it.displayName, it.id in enabled)
            },
        )
        refreshAccounts()
    }

    private suspend fun refreshAccounts() {
        val accounts = debrid.accounts()
        val rd = accounts.firstOrNull { it.provider == DebridProvider.REAL_DEBRID }
        val pm = accounts.firstOrNull { it.provider == DebridProvider.PREMIUMIZE }
        _state.value = _state.value.copy(
            realDebridConnected = rd?.isConnected == true,
            realDebridUser = rd?.username,
            premiumizeConnected = pm?.isConnected == true,
        )
    }

    fun setTmdbKey(value: String) = update(value) {
        _state.value = _state.value.copy(tmdbKey = it)
        viewModelScope.launch { settings.setTmdbApiKey(it) }
    }

    fun setOmdbKey(value: String) = update(value) {
        _state.value = _state.value.copy(omdbKey = it)
        viewModelScope.launch { settings.setOmdbApiKey(it) }
    }

    fun setPremiumizeKey(value: String) = update(value) {
        _state.value = _state.value.copy(premiumizeKey = it)
        viewModelScope.launch {
            settings.setPremiumizeApiKey(it)
            refreshAccounts()
        }
    }

    fun setOrionKey(value: String) = update(value) {
        _state.value = _state.value.copy(orionKey = it)
        viewModelScope.launch {
            settings.setOrionApiKey(it)
            // Enable Orion automatically once a key is present.
            settings.setScraperEnabled("orion", it.isNotBlank())
            refreshScrapers()
        }
    }

    fun setTorrentioUrl(value: String) {
        _state.value = _state.value.copy(torrentioUrl = value)
        viewModelScope.launch { settings.setTorrentioBaseUrl(value) }
    }

    fun setPreferredPlayer(id: String) {
        _state.value = _state.value.copy(preferredPlayer = id)
        viewModelScope.launch { settings.setPreferredPlayer(id) }
    }

    fun setOnlyCached(enabled: Boolean) {
        _state.value = _state.value.copy(onlyCached = enabled)
        viewModelScope.launch { settings.setOnlyCached(enabled) }
    }

    fun toggleScraper(id: String, enabled: Boolean) {
        viewModelScope.launch {
            settings.setScraperEnabled(id, enabled)
            refreshScrapers()
        }
    }

    fun toggleHomeRow(id: String, enabled: Boolean) {
        viewModelScope.launch { homeLayout.setEnabled(id, enabled) }
    }

    fun moveHomeRowUp(id: String) {
        viewModelScope.launch { homeLayout.moveUp(id) }
    }

    fun moveHomeRowDown(id: String) {
        viewModelScope.launch { homeLayout.moveDown(id) }
    }

    fun setFeaturedSource(kind: String) {
        _state.value = _state.value.copy(featuredSource = kind)
        viewModelScope.launch { settings.setFeaturedSource(kind) }
    }

    fun setDownloadFolder(uri: String?) {
        _state.value = _state.value.copy(downloadFolder = uri)
        viewModelScope.launch {
            settings.setDownloadFolderUri(uri)
            _state.value = _state.value.copy(
                statusMessage = if (uri == null) "Downloads will save to app storage." else "Download folder set.",
            )
        }
    }

    fun connectRealDebrid() {
        viewModelScope.launch {
            try {
                val code = realDebridAuth.startDeviceFlow()
                _state.value = _state.value.copy(
                    deviceCode = code,
                    statusMessage = "Visit ${code.verificationUrl} and enter code ${code.userCode}",
                )
                val attempts = (code.expiresIn / code.interval.coerceAtLeast(1)).coerceAtMost(120)
                repeat(attempts) {
                    delay(code.interval.coerceAtLeast(1) * 1000L)
                    if (realDebridAuth.tryComplete(code.deviceCode)) {
                        _state.value = _state.value.copy(
                            deviceCode = null,
                            statusMessage = "Real-Debrid connected!",
                        )
                        refreshAccounts()
                        return@launch
                    }
                }
                _state.value = _state.value.copy(
                    deviceCode = null,
                    statusMessage = "Authorization timed out — please try again.",
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    deviceCode = null,
                    statusMessage = "Couldn't start Real-Debrid sign-in: ${t.message}",
                )
            }
        }
    }

    fun disconnectRealDebrid() {
        viewModelScope.launch {
            realDebridAuth.disconnect()
            refreshAccounts()
            _state.value = _state.value.copy(statusMessage = "Real-Debrid disconnected.")
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(statusMessage = null)
    }

    private suspend fun refreshScrapers() {
        val enabled = settings.enabledScraperIds.first()
        _state.value = _state.value.copy(
            scrapers = scraperRepository.allScrapers.map {
                ScraperInfo(it.id, it.displayName, it.id in enabled)
            },
        )
    }

    private inline fun update(value: String, block: (String) -> Unit) = block(value)
}
