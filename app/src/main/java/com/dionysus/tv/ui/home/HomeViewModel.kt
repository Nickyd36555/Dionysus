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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    private suspend fun rebuild(snapshot: Snapshot) = coroutineScope {
        val enabledRows = snapshot.rows.filter { it.enabled }.sortedBy { it.position }

        // Fire every network fetch in parallel so first paint is bounded by the
        // slowest single call, not the sum of them.
        val kindsToFetch = (enabledRows.map { it.kind } + HomeRowKind.TRENDING)
            .filter { it in NETWORK_KINDS }
            .distinct()
        kindsToFetch.map { kind -> async { catalog(kind) } }.awaitAll()

        val catalogDefs = snapshot.addons
            .filter { it.providesCatalog }
            .flatMap { addon -> addon.manifest.catalogs.map { addon to it } }
        val addonFetched = catalogDefs.map { (addon, def) ->
            async {
                val key = "${addon.transportUrl}|${def.type}|${def.id}"
                // Only cache non-empty results so a transient failure retries later.
                val items = addonCatalogCache[key]
                    ?: addons.catalog(addon, def).also { if (it.isNotEmpty()) addonCatalogCache[key] = it }
                Triple(addon, def, items)
            }
        }.awaitAll()

        val builtIn = enabledRows.mapNotNull { row ->
            val items = when (row.kind) {
                HomeRowKind.CONTINUE_WATCHING -> snapshot.continueWatching.map { it.toMediaItem() }
                HomeRowKind.MY_LIST -> snapshot.favorites
                HomeRowKind.DOWNLOADS -> snapshot.downloads.map { it.toMediaItem() }
                else -> catalog(row.kind)
            }.distinctBy { it.id }
            if (items.isEmpty()) null else HomeRowUi(row.id, row.title, items)
        }

        val addonRows = addonFetched
            .filter { it.third.isNotEmpty() }
            .map { (addon, def, items) ->
                HomeRowUi(
                    "addon:${addon.transportUrl}|${def.type}|${def.id}",
                    catalogTitle(addon.manifest.name, def.name, def.type),
                    items.distinctBy { it.id },
                )
            }

        val rows = builtIn + addonRows
        val featured = (catalogCache[HomeRowKind.TRENDING] ?: emptyList())
            .ifEmpty { addonRows.firstOrNull()?.items.orEmpty() }

        _state.value = HomeUiState(
            featured = featured.take(8),
            rows = rows,
            isLoading = false,
            error = if (rows.isEmpty() && featured.isEmpty()) lastMetadataError else null,
        )
    }

    /** Distinct, readable title per catalog so movie/series rows don't collide. */
    private fun catalogTitle(addonName: String, catalogName: String, type: String): String {
        val base = catalogName.ifBlank { addonName }
        val typeLabel = when (type.lowercase()) {
            "movie", "movies" -> "Movies"
            "series", "tv", "show", "shows" -> "Shows"
            else -> type.replaceFirstChar { it.uppercase() }
        }
        return if (base.contains(typeLabel, ignoreCase = true)) base else "$base · $typeLabel"
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

    companion object {
        private val NETWORK_KINDS = setOf(
            HomeRowKind.TRENDING,
            HomeRowKind.POPULAR_MOVIES,
            HomeRowKind.POPULAR_SHOWS,
            HomeRowKind.TOP_RATED_MOVIES,
        )
    }
}
