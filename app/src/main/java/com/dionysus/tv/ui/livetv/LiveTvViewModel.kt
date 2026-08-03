package com.dionysus.tv.ui.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dionysus.tv.core.model.DataResult
import com.dionysus.tv.core.model.MediaItem
import com.dionysus.tv.data.iptv.CategoryGrouping
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
/** selectedCategory prefix meaning "every channel in this parent group". */
const val GROUP_ALL_PREFIX = "grp:"

enum class LiveTvMode { CHANNELS, VOD, GUIDE }

/** A rail row: either a parent group (drillable) or a leaf category, with a count. */
data class CategoryRow(
    val label: String,
    val value: String,
    val count: Int,
    val isGroup: Boolean,
    val hidden: Boolean = false,
)

data class LiveTvUiState(
    val hasPlaylists: Boolean = true,
    val hasVod: Boolean = false,
    val isLoading: Boolean = true,
    val mode: LiveTvMode = LiveTvMode.CHANNELS,
    val selectedCategory: String = CATEGORY_ALL,
    val selectedGroup: String? = null,
    val selectedVodCategory: String = CATEGORY_ALL_VOD,
    val channels: List<Channel> = emptyList(),
    val vod: List<MediaItem> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val hiddenGroups: Set<String> = emptySet(),
    val showHidden: Boolean = false,
    val epg: Map<String, List<Programme>> = emptyMap(),
    val error: String? = null,
) {
    private fun visibleGroupChannels() =
        channels.filter { CategoryGrouping.group(it.group) !in hiddenGroups }

    /** Channels shown for the current channel category. */
    val visibleChannels: List<Channel>
        get() = when {
            selectedCategory == CATEGORY_FAVORITES -> channels.filter { it.id in favorites }
            selectedCategory == CATEGORY_ALL -> visibleGroupChannels()
            selectedCategory.startsWith(GROUP_ALL_PREFIX) -> {
                val g = selectedCategory.removePrefix(GROUP_ALL_PREFIX)
                channels.filter { CategoryGrouping.group(it.group) == g }
            }
            else -> channels.filter { it.group == selectedCategory }
        }

    /** Top-level rail rows: Favorites, All, then parent groups (hidden filtered). */
    val topRows: List<CategoryRow>
        get() {
            val byGroup = channels.groupBy { CategoryGrouping.group(it.group) }
            val groupRows = byGroup.entries
                .sortedBy { it.key }
                .mapNotNull { (group, chans) ->
                    val isHidden = group in hiddenGroups
                    if (isHidden && !showHidden) null
                    else CategoryRow(group, group, chans.size, isGroup = true, hidden = isHidden)
                }
            return buildList {
                add(CategoryRow(CATEGORY_FAVORITES, CATEGORY_FAVORITES, favorites.size, isGroup = false))
                add(CategoryRow(CATEGORY_ALL, CATEGORY_ALL, visibleGroupChannels().size, isGroup = false))
                addAll(groupRows)
            }
        }

    /** Sub-category rows for the drilled-into group, with an "All in group" entry. */
    val subRows: List<CategoryRow>
        get() {
            val group = selectedGroup ?: return emptyList()
            val inGroup = channels.filter { CategoryGrouping.group(it.group) == group }
            val subs = inGroup.groupBy { it.group }.entries
                .sortedBy { CategoryGrouping.sub(it.key) }
                .map { (cat, chans) -> CategoryRow(CategoryGrouping.sub(cat), cat, chans.size, isGroup = false) }
            return buildList {
                add(CategoryRow("All $group", GROUP_ALL_PREFIX + group, inGroup.size, isGroup = false))
                addAll(subs)
            }
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
        get() = visibleGroupChannels().filter { it.epgId != null && epg[it.epgId].orEmpty().isNotEmpty() }
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
        iptv.hiddenGroups
            .onEach { hidden -> _state.value = _state.value.copy(hiddenGroups = hidden) }
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
                    _state.value = _state.value.copy(
                        hasPlaylists = hasPlaylists,
                        hasVod = vod.isNotEmpty(),
                        isLoading = false,
                        channels = channels,
                        vod = vod,
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

    /** Handle a tap on a top-level rail row (Favorites/All/leaf/group). */
    fun onRowSelected(row: CategoryRow) {
        if (!row.isGroup) {
            _state.value = _state.value.copy(selectedCategory = row.value)
            return
        }
        // A parent group: drill in if it has multiple sub-categories, else filter.
        val subs = _state.value.channels
            .filter { CategoryGrouping.group(it.group) == row.value }
            .map { it.group }.distinct()
        if (subs.size > 1) {
            _state.value = _state.value.copy(
                selectedGroup = row.value,
                selectedCategory = GROUP_ALL_PREFIX + row.value,
            )
        } else {
            _state.value = _state.value.copy(selectedCategory = subs.firstOrNull() ?: row.value)
        }
    }

    fun selectSub(row: CategoryRow) {
        _state.value = _state.value.copy(selectedCategory = row.value)
    }

    fun backToGroups() {
        _state.value = _state.value.copy(selectedGroup = null, selectedCategory = CATEGORY_ALL)
    }

    fun hideGroup(group: String) {
        viewModelScope.launch { iptv.toggleHiddenGroup(group) }
    }

    fun toggleShowHidden() {
        _state.value = _state.value.copy(showHidden = !_state.value.showHidden)
    }

    /** Upcoming programmes for a channel (now onward), for the guide rows. */
    fun upcoming(channel: Channel, limit: Int = 12): List<Programme> {
        val epgId = channel.epgId ?: return emptyList()
        val list = _state.value.epg[epgId] ?: return emptyList()
        val now = System.currentTimeMillis()
        return list.filter { it.stopMs > now }.take(limit)
    }

    /** All EPG programmes for a channel (sorted), for the timeline guide. */
    fun programmes(channel: Channel): List<Programme> {
        val epgId = channel.epgId ?: return emptyList()
        return _state.value.epg[epgId].orEmpty()
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
