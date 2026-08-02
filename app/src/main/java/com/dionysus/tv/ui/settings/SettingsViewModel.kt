package com.dionysus.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dionysus.tv.core.model.DebridProvider
import com.dionysus.tv.core.model.HomeRow
import com.dionysus.tv.data.debrid.DebridRepository
import com.dionysus.tv.data.debrid.realdebrid.RealDebridAuth
import com.dionysus.tv.data.debrid.realdebrid.RdDeviceCode
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
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val realDebridAuth: RealDebridAuth,
    private val debrid: DebridRepository,
    private val scraperRepository: ScraperRepository,
    private val homeLayout: HomeLayoutRepository,
    private val playerLauncher: PlayerLauncher,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { loadInitial() }
        homeLayout.rows
            .onEach { rows -> _state.value = _state.value.copy(homeRows = rows) }
            .launchIn(viewModelScope)
    }

    private suspend fun loadInitial() {
        val enabled = settings.enabledScraperIds.first()
        _state.value = _state.value.copy(
            tmdbKey = settings.currentTmdbApiKey().orEmpty(),
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
