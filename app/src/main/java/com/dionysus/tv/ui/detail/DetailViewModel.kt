package com.dionysus.tv.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dionysus.tv.core.model.DataResult
import com.dionysus.tv.core.model.Episode
import com.dionysus.tv.core.model.MediaItem
import com.dionysus.tv.core.model.MediaType
import com.dionysus.tv.core.model.Season
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
    private val library: LibraryRepository,
) : ViewModel() {

    val mediaId: String = savedStateHandle.get<String>("mediaId").orEmpty()

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        load()
        library.isFavorite(mediaId)
            .onEach { fav -> _state.value = _state.value.copy(isFavorite = fav) }
            .launchIn(viewModelScope)
    }

    private fun load() {
        val parsed = parseMediaId(mediaId)
        if (parsed == null) {
            _state.value = _state.value.copy(isLoading = false, error = "Invalid media id")
            return
        }
        val (type, tmdbId) = parsed
        viewModelScope.launch {
            when (type) {
                MediaType.MOVIE -> when (val r = metadata.movieDetail(tmdbId)) {
                    is DataResult.Success -> _state.value =
                        _state.value.copy(item = r.data, isLoading = false)
                    is DataResult.Error -> _state.value =
                        _state.value.copy(isLoading = false, error = r.message)
                }

                MediaType.TV_SHOW -> when (val r = metadata.tvDetail(tmdbId)) {
                    is DataResult.Success -> {
                        val (item, seasons) = r.data
                        _state.value = _state.value.copy(
                            item = item,
                            seasons = seasons,
                            isLoading = false,
                        )
                        seasons.firstOrNull()?.let { selectSeason(it.seasonNumber) }
                    }

                    is DataResult.Error -> _state.value =
                        _state.value.copy(isLoading = false, error = r.message)
                }
            }
        }
    }

    fun selectSeason(seasonNumber: Int) {
        val item = _state.value.item ?: return
        val tmdbId = item.tmdbId ?: return
        _state.value = _state.value.copy(selectedSeason = seasonNumber, episodes = emptyList())
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

    private fun parseMediaId(id: String): Pair<MediaType, Int>? {
        val parts = id.split(":")
        if (parts.size != 3 || parts[0] != "tmdb") return null
        val type = if (parts[1] == "tv") MediaType.TV_SHOW else MediaType.MOVIE
        val tmdbId = parts[2].toIntOrNull() ?: return null
        return type to tmdbId
    }
}
