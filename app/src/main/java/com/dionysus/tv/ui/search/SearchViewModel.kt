package com.dionysus.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dionysus.tv.core.model.MediaItem
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
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val metadata: MetadataRepository,
    private val iptv: IptvRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

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
    }
}
