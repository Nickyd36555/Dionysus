package com.dionysus.tv.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dionysus.tv.core.model.DataResult
import com.dionysus.tv.core.model.Episode
import com.dionysus.tv.core.model.MediaItem
import com.dionysus.tv.core.model.MediaType
import com.dionysus.tv.core.model.MovieExtra
import com.dionysus.tv.core.model.Season
import com.dionysus.tv.data.addons.AddonMeta
import com.dionysus.tv.data.addons.AddonRepository
import com.dionysus.tv.data.addons.toMediaItem
import com.dionysus.tv.data.ai.AiRecommendationRepository
import com.dionysus.tv.data.local.LibraryRepository
import com.dionysus.tv.data.metadata.MetadataRepository
import com.dionysus.tv.data.metadata.omdb.OmdbRepository
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
    val resumePositionMs: Long = 0L,
    val extra: MovieExtra? = null,
    val similar: List<MediaItem> = emptyList(),
    val similarLoading: Boolean = false,
    val similarMessage: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val metadata: MetadataRepository,
    private val addons: AddonRepository,
    private val library: LibraryRepository,
    private val omdb: OmdbRepository,
    private val ai: AiRecommendationRepository,
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
        viewModelScope.launch {
            // Movies save progress under their mediaId; a value here means "in progress".
            _state.value = _state.value.copy(resumePositionMs = library.resumePosition(mediaId))
        }
    }

    private fun load() {
        viewModelScope.launch {
            when {
                mediaId.startsWith("stremio:") -> loadStremio()
                mediaId.startsWith("tmdb:") -> loadTmdb()
                else -> _state.value = _state.value.copy(isLoading = false, error = "Unsupported media id")
            }
            // Enrich with IMDb/RT/Metacritic data once the base item is known.
            _state.value.item?.let { item ->
                val extra = omdb.extra(item.imdbId, item.title, item.year)
                if (extra != null) {
                    _state.value = _state.value.copy(
                        extra = extra,
                        // Prefer OMDb's fuller plot when TMDB's overview is empty.
                        item = _state.value.item?.let { cur ->
                            if (cur.overview.isBlank() && extra.plot != null) cur.copy(overview = extra.plot) else cur
                        },
                    )
                }
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

    /** Ask Claude for titles similar to this one, resolved to real cards. */
    fun findSimilar() {
        val item = _state.value.item ?: return
        if (_state.value.similarLoading) return
        val query = item.title + (item.year?.let { " ($it)" } ?: "")
        viewModelScope.launch {
            _state.value = _state.value.copy(similarLoading = true, similarMessage = null)
            when (val r = ai.similarTo(query)) {
                is DataResult.Success -> {
                    val picks = r.data.filter { it.id != item.id }
                    _state.value = _state.value.copy(
                        similar = picks,
                        similarLoading = false,
                        similarMessage = if (picks.isEmpty()) "No AI matches found." else null,
                    )
                }
                is DataResult.Error -> _state.value = _state.value.copy(
                    similarLoading = false,
                    similarMessage = r.message,
                )
            }
        }
    }
}
