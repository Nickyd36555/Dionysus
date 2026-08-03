package com.dionysus.tv.ui.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dionysus.tv.core.model.DataResult
import com.dionysus.tv.core.model.MediaItem
import com.dionysus.tv.data.iptv.Channel
import com.dionysus.tv.data.iptv.IptvRepository
import com.dionysus.tv.data.iptv.NowNext
import com.dionysus.tv.data.iptv.Programme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

const val CATEGORY_FAVORITES = "★ Favorites"
const val CATEGORY_ALL = "All Channels"
const val CATEGORY_ALL_VOD = "All Movies"

enum class LiveTvMode { CHANNELS, VOD, GUIDE }

data class LiveTvUiState(
    val hasPlaylists: Boolean = true,
    val hasVod: Boolean = false,
    val isLoading: Boolean = true,
    val mode: LiveTvMode = LiveTvMode.CHANNELS,
    val categories: List<String> = emptyList(),
    val selectedCategory: String = CATEGORY_ALL,
    val selectedVodCategory: String = CATEGORY_ALL_VOD,
    val channels: List<Channel> = emptyList(),
    val vod: List<MediaItem> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val epg: Map<String, List<Programme>> = emptyMap(),
    val error: String? = null,
) {
    /** Channels shown for the current channel category. */
    val visibleChannels: List<Channel>
        get() = when (selectedCategory) {
            CATEGORY_FAVORITES -> channels.filter { it.id in favorites }
            CATEGORY_ALL -> channels
            else -> channels.filter { it.group == selectedCategory }
        }

    /** VOD categories (from the provider), with an "All" entry first. */
    val vodCategories: List<String>
        get() = listOf(CATEGORY_ALL_VOD) + vod.flatMap { it.genres }.distinct().sorted()

    /** VOD movies shown for the current VOD category. */
    val visibleVod: List<MediaItem>
        get() = if (selectedVodCategory == CATEGORY_ALL_VOD) vod
        else vod.filter { selectedVodCategory in it.genres }

    /** Channels that have EPG data, used to populate the guide. */
    val guideChannels: List<Channel>
        get() = channels.filter { it.epgId != null && epg[it.epgId].orEmpty().isNotEmpty() }
}

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val iptv: IptvRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveTvUiState())
    val state: StateFlow<LiveTvUiState> = _state.asStateFlow()

    init {
        iptv.favorites
            .onEach { favs -> _state.value = _state.value.copy(favorites = favs) }
            .launchIn(viewModelScope)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val hasPlaylists = runCatching { iptv.playlists.first().isNotEmpty() }.getOrDefault(false)
            val vod = iptv.loadVod().getOrNull().orEmpty()
            when (val result = iptv.loadChannels()) {
                is DataResult.Success -> {
                    val channels = result.data
                    val groups = channels.map { it.group }.distinct().sorted()
                    _state.value = _state.value.copy(
                        hasPlaylists = hasPlaylists,
                        hasVod = vod.isNotEmpty(),
                        isLoading = false,
                        channels = channels,
                        vod = vod,
                        categories = buildList {
                            add(CATEGORY_FAVORITES)
                            add(CATEGORY_ALL)
                            addAll(groups)
                        },
                        error = if (channels.isEmpty() && vod.isEmpty() && hasPlaylists) "No channels found in your playlists." else null,
                    )
                }
                is DataResult.Error -> _state.value = _state.value.copy(
                    isLoading = false,
                    hasPlaylists = hasPlaylists,
                    hasVod = vod.isNotEmpty(),
                    vod = vod,
                    error = result.message,
                )
            }
            loadEpg()
        }
    }

    fun setMode(mode: LiveTvMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    fun selectVodCategory(category: String) {
        _state.value = _state.value.copy(selectedVodCategory = category)
    }

    /** Upcoming programmes for a channel (now onward), for the guide rows. */
    fun upcoming(channel: Channel, limit: Int = 12): List<Programme> {
        val epgId = channel.epgId ?: return emptyList()
        val list = _state.value.epg[epgId] ?: return emptyList()
        val now = System.currentTimeMillis()
        return list.filter { it.stopMs > now }.take(limit)
    }

    private fun loadEpg() {
        viewModelScope.launch {
            val epg = withContext(Dispatchers.Default) { iptv.loadEpg() }
            if (epg.isNotEmpty()) _state.value = _state.value.copy(epg = epg)
        }
    }

    fun selectCategory(category: String) {
        _state.value = _state.value.copy(selectedCategory = category)
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch { iptv.toggleFavorite(channel.id) }
    }

    /** Now/next for a channel, computed against the current wall-clock. */
    fun nowNext(channel: Channel): NowNext {
        val epgId = channel.epgId ?: return NowNext()
        val list = _state.value.epg[epgId] ?: return NowNext()
        val now = System.currentTimeMillis()
        val current = list.firstOrNull { now in it.startMs until it.stopMs }
        val next = list.firstOrNull { it.startMs >= now }
        return NowNext(current, next)
    }
}
