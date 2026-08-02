package com.dionysus.tv.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dionysus.tv.core.model.DataResult
import com.dionysus.tv.core.model.Episode
import com.dionysus.tv.core.model.MediaItem
import com.dionysus.tv.core.model.MediaType
import com.dionysus.tv.core.model.Season
import com.dionysus.tv.data.addons.AddonMeta
import com.dionysus.tv.data.addons.AddonRepository
import com.dionysus.tv.data.addons.toMediaItem
import com.dionysus.tv.data.local.LibraryRepository
import com.dionysus.tv.data.metadata.MetadataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val item: MediaItem? = null,
    val seasons: List<Season> = emptyList(),
    val selectedSeason: Int? = null,
    val episodes: List<Episode> = emptyList(),
    val isFavorite: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val metadata: MetadataRepository,
    private val addons: AddonRepository,
    private val library: LibraryRepository,
) : ViewModel() {

    val mediaId: String = savedStateHandle.get<String>("mediaId").orEmpty()

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    /** Cached Stremio meta so season selection doesn't re-fetch. */
    private var stremioMeta: AddonMeta? = null

    init {
        load()
        library.isFavorite(mediaId)
            .onEach { fav -> _state.value = _state.value.copy(isFavorite = fav) }
            .launchIn(viewModelScope)
    }

    private fun load() {
        viewModelScope.launch {
            when {
                mediaId.startsWith("stremio:") -> loadStremio()
                mediaId.startsWith("tmdb:") -> loadTmdb()
                else -> _state.value = _state.value.copy(isLoading = false, error = "Unsupported media id")
            }
        }
    }

    private suspend fun loadStremio() {
        val parts = mediaId.split(":", limit = 3)
        if (parts.size < 3) {
            _state.value = _state.value.copy(isLoading = false, error = "Invalid media id")
            return
        }
        val stremType = parts[1]
        val meta = addons.meta(stremType, parts[2])
        if (meta == null) {
            _state.value = _state.value.copy(isLoading = false, error = "Couldn't load details from add-ons.")
            return
        }
        stremioMeta = meta
        val item = meta.toMediaItem()
        if (item.type == MediaType.TV_SHOW) {
            val seasons = meta.videos.mapNotNull { it.season }
                .filter { it > 0 }
                .distinct()
                .sorted()
                .map { s -> Season(s, "Season $s", meta.videos.count { it.season == s }) }
            _state.value = _state.value.copy(item = item, seasons = seasons, isLoading = false)
            seasons.firstOrNull()?.let { selectSeason(it.seasonNumber) }
        } else {
            _state.value = _state.value.copy(item = item, isLoading = false)
        }
    }

    private suspend fun loadTmdb() {
        val parts = mediaId.split(":")
        val tmdbId = parts.getOrNull(2)?.toIntOrNull()
        if (parts.size != 3 || tmdbId == null) {
            _state.value = _state.value.copy(isLoading = false, error = "Invalid media id")
            return
        }
        if (parts[1] == "tv") {
            when (val r = metadata.tvDetail(tmdbId)) {
                is DataResult.Success -> {
                    val (item, seasons) = r.data
                    _state.value = _state.value.copy(item = item, seasons = seasons, isLoading = false)
                    seasons.firstOrNull()?.let { selectSeason(it.seasonNumber) }
                }
                is DataResult.Error -> _state.value = _state.value.copy(isLoading = false, error = r.message)
            }
        } else {
            when (val r = metadata.movieDetail(tmdbId)) {
                is DataResult.Success -> _state.value = _state.value.copy(item = r.data, isLoading = false)
                is DataResult.Error -> _state.value = _state.value.copy(isLoading = false, error = r.message)
            }
        }
    }

    fun selectSeason(seasonNumber: Int) {
        _state.value = _state.value.copy(selectedSeason = seasonNumber, episodes = emptyList())

        val meta = stremioMeta
        if (meta != null) {
            val eps = meta.videos
                .filter { it.season == seasonNumber }
                .sortedBy { it.episode ?: 0 }
                .map {
                    Episode(
                        seasonNumber = it.season ?: seasonNumber,
                        episodeNumber = it.episode ?: 0,
                        title = it.name,
                        overview = it.overview,
                        stillUrl = it.thumbnail,
                        airDate = it.released,
                    )
                }
            _state.value = _state.value.copy(episodes = eps)
            return
        }

        val tmdbId = _state.value.item?.tmdbId ?: return
        viewModelScope.launch {
            when (val r = metadata.episodes(tmdbId, seasonNumber)) {
                is DataResult.Success -> _state.value = _state.value.copy(episodes = r.data)
                is DataResult.Error -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    fun toggleFavorite() {
        val item = _state.value.item ?: return
        viewModelScope.launch {
            library.toggleFavorite(item, makeFavorite = !_state.value.isFavorite)
        }
    }
}
