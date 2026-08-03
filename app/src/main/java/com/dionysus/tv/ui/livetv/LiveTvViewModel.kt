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
import com.dionysus.tv.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

enum class LiveTvMode { LIVE, VOD }

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
    val mode: LiveTvMode = LiveTvMode.LIVE,
    val selectedCategory: String = CATEGORY_ALL,
    val selectedGroup: String? = null,
    val selectedVodCategory: String = CATEGORY_ALL_VOD,
    val channels: List<Channel> = emptyList(),
    val vod: List<MediaItem> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val hiddenGroups: Set<String> = emptySet(),
    val showHidden: Boolean = false,
    val epg: Map<String, List<Programme>> = emptyMap(),
    /** Per-channel EPG (keyed by channel id) fetched on demand via get_short_epg. */
    val shortEpg: Map<String, List<Programme>> = emptyMap(),
    val epgLoading: Boolean = false,
    val epgOffsetMinutes: Int = 0,
    val guideTimeZone: String = "",
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

    /** VOD parent groups (US, UK, 24/7, …), with an "All" entry first. */
    val vodCategories: List<String>
        get() = listOf(CATEGORY_ALL_VOD) +
            vod.flatMap { it.genres }.map { CategoryGrouping.group(it) }.distinct().sorted()

    /** VOD movies shown for the current VOD group. */
    val visibleVod: List<MediaItem>
        get() = if (selectedVodCategory == CATEGORY_ALL_VOD) vod
        else vod.filter { item -> item.genres.any { CategoryGrouping.group(it) == selectedVodCategory } }

    /** Channels that have EPG data, used to populate the guide. */
    val guideChannels: List<Channel>
        get() = visibleGroupChannels().filter { it.epgId != null && epg[it.epgId].orEmpty().isNotEmpty() }
}

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val iptv: IptvRepository,
    private val settings: SettingsRepository,
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
        settings.epgOffsetMinutes
            .onEach { off -> _state.value = _state.value.copy(epgOffsetMinutes = off) }
            .launchIn(viewModelScope)
        settings.guideTimeZone
            .onEach { tz -> _state.value = _state.value.copy(guideTimeZone = tz) }
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

    /** All EPG programmes for a channel (sorted): XMLTV if present, else per-channel. */
    fun programmes(channel: Channel): List<Programme> {
        val fromXmltv = channel.epgId?.let { _state.value.epg[iptv.normEpgId(it)] }.orEmpty()
        val base = fromXmltv.ifEmpty { _state.value.shortEpg[channel.id].orEmpty() }
        val offset = _state.value.epgOffsetMinutes * 60_000L
        return if (offset == 0L) base
        else base.map { it.copy(startMs = it.startMs + offset, stopMs = it.stopMs + offset) }
    }

    /** Upcoming programmes for a channel (now onward), for the guide rows. */
    fun upcoming(channel: Channel, limit: Int = 12): List<Programme> {
        val now = System.currentTimeMillis()
        return programmes(channel).filter { it.stopMs > now }.take(limit)
    }

    /**
     * Fetch per-channel EPG for the visible channels that don't already have data.
     * Xtream providers frequently ship an empty global xmltv.php, so this is what
     * actually fills the guide. Capped and de-duplicated to avoid hammering the API.
     */
    fun prefetchGuide(channels: List<Channel>) {
        viewModelScope.launch {
            val have = _state.value.shortEpg
            val todo = channels.asSequence()
                .filter { it.xtreamStreamId != null }
                .filter { it.id !in have }
                .filter { ch -> ch.epgId?.let { _state.value.epg[iptv.normEpgId(it)]?.isNotEmpty() } != true }
                .take(60)
                .toList()
            if (todo.isEmpty()) return@launch
            val fetched = coroutineScope {
                todo.map { ch -> async { ch.id to iptv.shortEpg(ch) } }.awaitAll()
            }.filter { it.second.isNotEmpty() }
            if (fetched.isNotEmpty()) {
                _state.value = _state.value.copy(shortEpg = _state.value.shortEpg + fetched)
            }
        }
    }

    private fun loadEpg() {
        viewModelScope.launch {
            // Instant: show any cached/disk EPG right away…
            val instant = iptv.cachedEpgOrDisk()
            if (instant.isNotEmpty()) _state.value = _state.value.copy(epg = instant)
            // …then refresh in the background (one bulk XMLTV download), with a
            // visible indicator so it doesn't look frozen.
            _state.value = _state.value.copy(epgLoading = true)
            val fresh = iptv.loadEpg()
            _state.value = _state.value.copy(
                epg = if (fresh.isNotEmpty()) fresh else _state.value.epg,
                epgLoading = false,
            )
        }
    }

    /** Force a fresh bulk EPG download (from the manual refresh button). */
    fun refreshEpg() {
        viewModelScope.launch {
            _state.value = _state.value.copy(epgLoading = true)
            iptv.invalidateCache()
            val fresh = iptv.loadEpg()
            _state.value = _state.value.copy(
                epg = if (fresh.isNotEmpty()) fresh else _state.value.epg,
                epgLoading = false,
            )
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
        val list = programmes(channel)
        if (list.isEmpty()) return NowNext()
        val now = System.currentTimeMillis()
        val current = list.firstOrNull { now in it.startMs until it.stopMs }
        val next = list.firstOrNull { it.startMs >= now }
        return NowNext(current, next)
    }
}
