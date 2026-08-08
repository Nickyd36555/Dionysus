package com.dionysus.tv.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dionysus.tv.core.model.DataResult
import com.dionysus.tv.core.model.MediaItem
import com.dionysus.tv.data.ai.AiRecommendationRepository
import com.dionysus.tv.data.iptv.IptvRepository
import com.dionysus.tv.data.metadata.MetadataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val metadata: MetadataRepository,
    private val iptv: IptvRepository,
    private val ai: AiRecommendationRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // A Home tile (or deep link) can open Search pre-filled via the "q" route arg.
    private val _query = MutableStateFlow(savedStateHandle.get<String>("q").orEmpty())
    val query: StateFlow<String> = _query.asStateFlow()

    /** AI "Similar To" results (Claude → resolved to real cards), and its status. */
    private val _aiResults = MutableStateFlow<List<MediaItem>>(emptyList())
    val aiResults: StateFlow<List<MediaItem>> = _aiResults.asStateFlow()

    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()

    private val _aiMessage = MutableStateFlow<String?>(null)
    val aiMessage: StateFlow<String?> = _aiMessage.asStateFlow()

    /**
     * Aggregated results across the movie catalog (Dionysus), Xtream VOD, and
     * Live TV (things airing per EPG). Sources run concurrently; each item keeps
     * its own [com.dionysus.tv.core.model.MediaSource] tag so the UI can badge it.
     */
    val results: StateFlow<List<MediaItem>> = _query
        .debounce(350)
        .distinctUntilChanged()
        .mapLatest { raw ->
            val q = raw.trim()
            if (q.length < 2) return@mapLatest emptyList()
            coroutineScope {
                val catalog = async { metadata.search(q).getOrNull().orEmpty() }
                val iptvHits = async { runCatching { iptv.searchContent(q) }.getOrDefault(emptyList()) }
                (catalog.await() + iptvHits.await()).distinctBy { it.id }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        _query.value = value
        // A new query invalidates any prior AI recommendations.
        _aiResults.value = emptyList()
        _aiMessage.value = null
    }

    /** Ask Claude for titles similar to the current query, resolved to real cards. */
    fun aiSimilarSearch() {
        val q = _query.value.trim()
        if (q.length < 2 || _aiLoading.value) return
        viewModelScope.launch {
            _aiLoading.value = true
            _aiMessage.value = null
            when (val r = ai.similarTo(q)) {
                is DataResult.Success -> {
                    _aiResults.value = r.data
                    if (r.data.isEmpty()) _aiMessage.value = "No AI matches found for \"$q\"."
                }
                is DataResult.Error -> _aiMessage.value = r.message
            }
            _aiLoading.value = false
        }
    }
}
