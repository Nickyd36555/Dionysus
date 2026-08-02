package com.dionysus.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dionysus.tv.core.model.DataResult
import com.dionysus.tv.core.model.HomeRow
import com.dionysus.tv.core.model.HomeRowKind
import com.dionysus.tv.core.model.MediaItem
import com.dionysus.tv.core.model.MediaType
import com.dionysus.tv.data.addons.Addon
import com.dionysus.tv.data.addons.AddonRepository
import com.dionysus.tv.data.local.HomeLayoutRepository
import com.dionysus.tv.data.local.LibraryRepository
import com.dionysus.tv.data.local.entity.DownloadEntity
import com.dionysus.tv.data.local.entity.WatchProgressEntity
import com.dionysus.tv.data.metadata.MetadataRepository
import com.dionysus.tv.download.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeRowUi(val id: String, val title: String, val items: List<MediaItem>)

data class HomeUiState(
    val featured: List<MediaItem> = emptyList(),
    val rows: List<HomeRowUi> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val metadata: MetadataRepository,
    private val library: LibraryRepository,
    private val downloads: DownloadRepository,
    private val homeLayout: HomeLayoutRepository,
    private val addons: AddonRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val catalogCache = mutableMapOf<HomeRowKind, List<MediaItem>>()
    private val addonCatalogCache = mutableMapOf<String, List<MediaItem>>()
    private var lastMetadataError: String? = null

    private data class Snapshot(
        val rows: List<HomeRow>,
        val favorites: List<MediaItem>,
        val continueWatching: List<WatchProgressEntity>,
        val downloads: List<DownloadEntity>,
        val addons: List<Addon>,
    )

    init {
        viewModelScope.launch { homeLayout.ensureSeeded() }

        combine(
            homeLayout.rows,
            library.favorites(),
            library.continueWatching(),
            downloads.downloads(),
            addons.installedAddons,
        ) { rows, favs, cw, dl, adns -> Snapshot(rows, favs, cw, dl, adns) }
            .onEach { rebuild(it) }
            .launchIn(viewModelScope)
    }

    private suspend fun rebuild(snapshot: Snapshot) {
        val builtIn = snapshot.rows
            .filter { it.enabled }
            .sortedBy { it.position }
            .mapNotNull { row ->
                val items = when (row.kind) {
                    HomeRowKind.CONTINUE_WATCHING -> snapshot.continueWatching.map { it.toMediaItem() }
                    HomeRowKind.MY_LIST -> snapshot.favorites
                    HomeRowKind.DOWNLOADS -> snapshot.downloads.map { it.toMediaItem() }
                    else -> catalog(row.kind)
                }
                if (items.isEmpty()) null else HomeRowUi(row.id, row.title, items)
            }

        val addonRows = buildList {
            for (addon in snapshot.addons.filter { it.providesCatalog }) {
                for (def in addon.manifest.catalogs) {
                    val key = "${addon.transportUrl}|${def.type}|${def.id}"
                    val items = addonCatalogCache.getOrPut(key) { addons.catalog(addon, def) }
                    if (items.isNotEmpty()) {
                        val title = def.name.ifBlank { "${addon.manifest.name} · ${def.type}" }
                        add(HomeRowUi("addon:$key", title, items))
                    }
                }
            }
        }

        val rows = builtIn + addonRows
        val featured = catalog(HomeRowKind.TRENDING).ifEmpty { addonRows.firstOrNull()?.items.orEmpty() }

        _state.value = HomeUiState(
            featured = featured.take(8),
            rows = rows,
            isLoading = false,
            error = if (rows.isEmpty() && featured.isEmpty()) lastMetadataError else null,
        )
    }

    private suspend fun catalog(kind: HomeRowKind): List<MediaItem> {
        catalogCache[kind]?.let { return it }
        val result = when (kind) {
            HomeRowKind.TRENDING -> metadata.trending()
            HomeRowKind.POPULAR_MOVIES -> metadata.popularMovies()
            HomeRowKind.POPULAR_SHOWS -> metadata.popularShows()
            HomeRowKind.TOP_RATED_MOVIES -> metadata.topRatedMovies()
            else -> DataResult.Success(emptyList())
        }
        return when (result) {
            is DataResult.Success -> result.data.also { catalogCache[kind] = it }
            is DataResult.Error -> {
                lastMetadataError = result.message
                emptyList()
            }
        }
    }

    private fun WatchProgressEntity.toMediaItem() = MediaItem(
        id = mediaId,
        type = runCatching { MediaType.valueOf(type) }.getOrDefault(MediaType.MOVIE),
        title = title,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
    )

    private fun DownloadEntity.toMediaItem() = MediaItem(
        id = mediaId,
        type = MediaType.MOVIE,
        title = title,
        posterUrl = posterUrl,
    )
}
