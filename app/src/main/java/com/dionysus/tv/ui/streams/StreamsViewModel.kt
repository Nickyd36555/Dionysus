package com.dionysus.tv.ui.streams

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dionysus.tv.core.model.MediaItem
import com.dionysus.tv.core.model.MediaType
import com.dionysus.tv.core.model.StreamSource
import com.dionysus.tv.data.addons.AddonRepository
import com.dionysus.tv.data.addons.toMediaItem
import com.dionysus.tv.data.debrid.DebridRepository
import com.dionysus.tv.data.metadata.MetadataRepository
import com.dionysus.tv.data.scraper.ScraperRepository
import com.dionysus.tv.data.scraper.StreamQuery
import com.dionysus.tv.data.settings.SettingsRepository
import com.dionysus.tv.download.DownloadRepository
import com.dionysus.tv.player.ExternalPlayer
import com.dionysus.tv.player.PlayerLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Emitted when the internal player should open a resolved URL. */
data class InternalPlayback(val url: String, val title: String, val progressId: String)

data class StreamsUiState(
    val title: String = "",
    val backdropUrl: String? = null,
    val posterUrl: String? = null,
    val sources: List<StreamSource> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val resolvingTitle: String? = null,
    val message: String? = null,
)

@HiltViewModel
class StreamsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val metadata: MetadataRepository,
    private val addons: AddonRepository,
    private val scrapers: ScraperRepository,
    private val debrid: DebridRepository,
    private val downloads: DownloadRepository,
    private val settings: SettingsRepository,
    private val playerLauncher: PlayerLauncher,
) : ViewModel() {

    private val mediaId: String = savedStateHandle.get<String>("mediaId").orEmpty()
    private val season: Int? = savedStateHandle.get<Int>("season")?.takeIf { it >= 0 }
    private val episode: Int? = savedStateHandle.get<Int>("episode")?.takeIf { it >= 0 }

    private val _state = MutableStateFlow(StreamsUiState())
    val state: StateFlow<StreamsUiState> = _state.asStateFlow()

    private val _playback = MutableStateFlow<InternalPlayback?>(null)
    val playback: StateFlow<InternalPlayback?> = _playback.asStateFlow()

    private var mediaItem: MediaItem? = null
    private var stremioContentId: String? = null

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val item = resolveItem()
            if (item == null) {
                _state.value = _state.value.copy(isLoading = false, error = "Couldn't load media details.")
                return@launch
            }
            mediaItem = item
            _state.value = _state.value.copy(
                title = item.title.ifBlank { "Sources" },
                backdropUrl = item.backdropUrl,
                posterUrl = item.posterUrl,
            )

            if (item.imdbId == null && stremioContentId == null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "No id available for this title, so scrapers can't find sources.",
                )
                return@launch
            }

            val query = StreamQuery(
                type = item.type,
                title = item.title,
                year = item.year,
                imdbId = item.imdbId,
                tmdbId = item.tmdbId,
                stremioId = stremioContentId,
                season = season,
                episode = episode,
            )
            val found = scrapers.findStreams(query)
            val annotated = debrid.annotateCache(found)
            val onlyCached = settings.currentOnlyCached()
            val visible = if (onlyCached) {
                annotated.filter { it.cachedOn.isNotEmpty() || it.isDirect }
            } else {
                annotated
            }
            _state.value = _state.value.copy(
                sources = visible,
                isLoading = false,
                error = when {
                    visible.isNotEmpty() -> null
                    onlyCached && annotated.isNotEmpty() ->
                        "No cached sources found. Turn off \"Cached only\" in Settings to see all ${annotated.size} sources."
                    else -> "No sources found."
                },
            )
        }
    }

    fun play(source: StreamSource) {
        viewModelScope.launch {
            _state.value = _state.value.copy(resolvingTitle = source.title, message = null)
            val url = source.url ?: debrid.resolve(source)?.playbackUrl
            _state.value = _state.value.copy(resolvingTitle = null)
            if (url.isNullOrBlank()) {
                _state.value = _state.value.copy(
                    message = "Couldn't resolve this source — Premiumize only serves torrents it already has cached. " +
                        "Try a source marked cached (⚡), or use one with high seeders.",
                )
                return@launch
            }
            dispatchPlayback(url)
        }
    }

    fun download(source: StreamSource) {
        val item = mediaItem ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(message = "Starting download…")
            val id = downloads.startDownload(item, source)
            _state.value = _state.value.copy(
                message = if (id != null) "Download started." else "Couldn't start download.",
            )
        }
    }

    fun consumePlayback() { _playback.value = null }

    fun clearMessage() { _state.value = _state.value.copy(message = null) }

    private suspend fun dispatchPlayback(url: String) {
        val title = mediaItem?.title.orEmpty()
        val player = ExternalPlayer.fromId(settings.preferredPlayerId.first())
        if (player.isInternal) {
            _playback.value = InternalPlayback(url, title, progressId())
        } else {
            val launched = playerLauncher.launch(player, url, title)
            if (!launched) {
                _state.value = _state.value.copy(message = "${player.displayName} isn't installed — using built-in player.")
                _playback.value = InternalPlayback(url, title, progressId())
            }
        }
    }

    private fun progressId(): String =
        if (season != null && episode != null) "$mediaId:s${season}e${episode}" else mediaId

    private suspend fun resolveItem(): MediaItem? {
        val parts = mediaId.split(":", limit = 3)
        if (parts.size < 3) return null
        return when (parts[0]) {
            "stremio" -> {
                val stremType = parts[1]
                val sid = parts[2]
                stremioContentId = sid
                val meta = addons.meta(stremType, sid)
                meta?.toMediaItem() ?: MediaItem(
                    id = mediaId,
                    type = if (stremType == "series") MediaType.TV_SHOW else MediaType.MOVIE,
                    title = "Selected title",
                    imdbId = Regex("tt\\d+").find(sid)?.value,
                )
            }
            "tmdb" -> {
                val tmdbId = parts[2].toIntOrNull() ?: return null
                if (parts[1] == "tv") metadata.tvDetail(tmdbId).getOrNull()?.first
                else metadata.movieDetail(tmdbId).getOrNull()
            }
            else -> null
        }
    }
}
