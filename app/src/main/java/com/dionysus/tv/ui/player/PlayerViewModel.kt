package com.dionysus.tv.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dionysus.tv.core.model.MediaType
import com.dionysus.tv.data.local.LibraryRepository
import com.dionysus.tv.data.settings.SettingsRepository
import com.dionysus.tv.player.ExternalPlayer
import com.dionysus.tv.player.PlayerLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val library: LibraryRepository,
    private val playerLauncher: PlayerLauncher,
    settings: SettingsRepository,
) : ViewModel() {

    /**
     * Whether to bitstream Dolby/DTS/TrueHD/Atmos untouched to a receiver
     * (passthrough) instead of decoding to PCM. Read once at player start.
     */
    val audioPassthrough: StateFlow<Boolean> =
        settings.audioPassthrough.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** External players (Kodi/VLC/MX/…) that are actually installed on this box. */
    fun installedExternalPlayers(): List<ExternalPlayer> = playerLauncher.installedExternalPlayers()

    /** Hand the current stream off to an external player at the given position. */
    fun openExternally(player: ExternalPlayer, positionMs: Long): Boolean =
        playerLauncher.launch(player, url, title, positionMs)

    val url: String = savedStateHandle.get<String>("url").orEmpty()
    val title: String = savedStateHandle.get<String>("title").orEmpty()
    private val progressId: String = savedStateHandle.get<String>("progressId").orEmpty()
    private val poster: String? = savedStateHandle.get<String>("poster")?.takeIf { it.isNotBlank() }
    private val backdrop: String? = savedStateHandle.get<String>("backdrop")?.takeIf { it.isNotBlank() }

    /** Resume position in ms; null until loaded, then 0 or the saved point. */
    private val _startPositionMs = MutableStateFlow<Long?>(null)
    val startPositionMs: StateFlow<Long?> = _startPositionMs.asStateFlow()

    init {
        viewModelScope.launch {
            _startPositionMs.value = if (progressId.isBlank()) 0L else library.resumePosition(progressId)
        }
    }

    /** Persist the resume point so the title reappears in Continue Watching. */
    fun saveProgress(positionMs: Long, durationMs: Long) {
        if (progressId.isBlank() || durationMs <= 0) return
        val mediaId = if (progressId.contains(":s")) progressId.substringBefore(":s") else progressId
        val type = if (mediaId.contains(":tv:") || mediaId.contains(":series:")) MediaType.TV_SHOW else MediaType.MOVIE
        viewModelScope.launch {
            library.saveProgress(
                id = progressId,
                mediaId = mediaId,
                type = type,
                title = title,
                subtitle = null,
                posterUrl = poster,
                backdropUrl = backdrop,
                positionMs = positionMs,
                durationMs = durationMs,
            )
        }
    }
}
