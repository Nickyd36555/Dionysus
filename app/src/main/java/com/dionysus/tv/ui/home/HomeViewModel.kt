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
import com.dionysus.tv.data.settings.SettingsRepository
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

data class HomeRowUi(
    val id: String,
    val title: String,
    val items: List<MediaItem>,
    val isContinueWatching: Boolean = false,
)

data class HomeUiState(
    val featured: List<MediaItem> = emptyList(),
    val tiles: List<com.dionysus.tv.data.settings.HomeTile> = emptyList(),
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
    private val settings: SettingsRepository,
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

        // Quick tiles are cheap and independent of the (heavy) row rebuild, so collect
        // them separately — editing tiles in Settings updates Home without a refetch.
        settings.homeTiles
            .onEach { tiles -> _state.value = _state.value.copy(tiles = tiles) }
            .launchIn(viewModelScope)

        combine(
            homeLayout.rows,
            library.favorites(),
            library.continueWatching(),
            downloads.downloads(),
            addons.installedAddons,
        ) { rows, favs, cw, dl, adns -> Snapshot(rows, favs, cw, dl, adns) }
            .combine(settings.featuredSource) { snap, featured -> snap to featured }
            .onEach { (snap, featured) -> rebuild(snap, featured) }
            .launchIn(viewModelScope)
    }

    private suspend fun rebuild(snapshot: Snapshot, featuredKindName: String) = coroutineScope {
        // Map every available add-on catalog to a stable id/param and make sure a
        // config row exists for each so it can be toggled/reordered in Settings.
        val catalogDefs = snapshot.addons
            .filter { it.providesCatalog }
            .flatMap { addon -> addon.manifest.catalogs.map { addon to it } }
        val defByParam = catalogDefs.associateBy { (addon, def) -> paramOf(addon, def) }
        val addonConfigs = catalogDefs.map { (addon, def) ->
            val param = paramOf(addon, def)
            HomeRow(
                id = "addon:$param",
                kind = HomeRowKind.ADDON_CATALOG,
                title = catalogTitle(addon.manifest.name, def.name, def.type),
                position = 0,
                enabled = true,
                param = param,
            )
        }
        homeLayout.syncAddonCatalogs(addonConfigs)

        val enabledRows = snapshot.rows.filter { it.enabled }.sortedBy { it.position }

        // Fire network catalog fetches in parallel (bounded by slowest call).
        val kindsToFetch = (enabledRows.map { it.kind } + HomeRowKind.TRENDING)
            .filter { it in NETWORK_KINDS }.distinct()
        kindsToFetch.map { kind -> async { catalog(kind) } }.awaitAll()

        val rows = enabledRows.mapNotNull { row ->
            val items = when (row.kind) {
                HomeRowKind.CONTINUE_WATCHING -> snapshot.continueWatching.map { it.toMediaItem() }
                HomeRowKind.MY_LIST -> snapshot.favorites
                HomeRowKind.DOWNLOADS -> snapshot.downloads.map { it.toMediaItem() }
                HomeRowKind.ADDON_CATALOG -> {
                    val pd = row.param?.let { defByParam[it] }
                    if (pd == null) emptyList() else fetchAddon(pd.first, pd.second)
                }
                else -> catalog(row.kind)
            }.distinctBy { it.id }
            if (items.isEmpty()) null
            else HomeRowUi(row.id, row.title, items, isContinueWatching = row.kind == HomeRowKind.CONTINUE_WATCHING)
        }

        val featured = featuredItems(featuredKindName, snapshot)
            .ifEmpty { rows.firstOrNull { !it.isContinueWatching }?.items.orEmpty() }

        _state.value = HomeUiState(
            featured = featured.take(8),
            rows = rows,
            isLoading = false,
            error = if (rows.isEmpty() && featured.isEmpty()) lastMetadataError else null,
        )
    }

    private fun paramOf(addon: Addon, def: com.dionysus.tv.data.addons.AddonCatalogDef): String =
        "${addon.transportUrl}|${def.type}|${def.id}"

    private suspend fun fetchAddon(addon: Addon, def: com.dionysus.tv.data.addons.AddonCatalogDef): List<MediaItem> {
        val key = paramOf(addon, def)
        addonCatalogCache[key]?.let { return it }
        return addons.catalog(addon, def).also { if (it.isNotEmpty()) addonCatalogCache[key] = it }
    }

    private suspend fun featuredItems(kindName: String, snapshot: Snapshot): List<MediaItem> {
        return when (HomeRowKind.fromName(kindName)) {
            HomeRowKind.MY_LIST -> snapshot.favorites
            HomeRowKind.CONTINUE_WATCHING -> snapshot.continueWatching.map { it.toMediaItem() }
            HomeRowKind.POPULAR_MOVIES -> catalog(HomeRowKind.POPULAR_MOVIES)
            HomeRowKind.POPULAR_SHOWS -> catalog(HomeRowKind.POPULAR_SHOWS)
            HomeRowKind.TOP_RATED_MOVIES -> catalog(HomeRowKind.TOP_RATED_MOVIES)
            else -> catalog(HomeRowKind.TRENDING)
        }
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

    /** Remove a title from Continue Watching (from the long-press menu). */
    fun removeFromContinueWatching(mediaId: String) {
        viewModelScope.launch { library.removeProgressForMedia(mediaId) }
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
