package com.dionysus.tv.data.addons

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dionysus.tv.core.model.DataResult
import com.dionysus.tv.core.model.MediaItem
import com.dionysus.tv.core.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

private val Context.addonStore by preferencesDataStore(name = "dionysus_addons")

/** An installed Stremio addon plus the base URL its resources are served from. */
data class Addon(
    val transportUrl: String,
    val manifest: AddonManifest,
) {
    val base: String = transportUrl.substringBeforeLast('/', "").ifEmpty { transportUrl }.let {
        if (it.endsWith("/")) it else "$it/"
    }

    val resourceNames: Set<String> = manifest.resources.mapNotNull { el ->
        when (el) {
            is JsonPrimitive -> el.contentOrNull
            is JsonObject -> (el["name"] as? JsonPrimitive)?.contentOrNull
            else -> null
        }
    }.toSet()

    val providesStream: Boolean get() = "stream" in resourceNames
    val providesCatalog: Boolean get() = "catalog" in resourceNames && manifest.catalogs.isNotEmpty()
    val providesMeta: Boolean get() = "meta" in resourceNames
}

/** Persisted form of an installed addon. */
@Serializable
data class StoredAddon(val transportUrl: String, val manifest: AddonManifest)

/**
 * Installed addons are persisted in DataStore (same durable store as the app's
 * other settings) so they survive restarts and updates.
 */
@Singleton
class AddonRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: AddonApi,
    private val json: Json,
) {
    private val listSerializer = ListSerializer(StoredAddon.serializer())

    val installedAddons: Flow<List<Addon>> = context.addonStore.data.map { prefs ->
        decode(prefs[KEY]).map { Addon(it.transportUrl, it.manifest) }
    }

    suspend fun current(): List<Addon> = installedAddons.first()

    /** Fetch a manifest from [manifestUrl] and persist the addon. */
    suspend fun install(manifestUrl: String): DataResult<Addon> = DataResult.catching {
        val url = manifestUrl.trim()
        val manifest = api.manifest(url)
        val stored = readStored().toMutableList()
        stored.removeAll { it.transportUrl == url }
        stored.add(StoredAddon(url, manifest))
        writeStored(stored)
        Addon(url, manifest)
    }

    suspend fun remove(transportUrl: String) {
        writeStored(readStored().filterNot { it.transportUrl == transportUrl })
    }

    private suspend fun readStored(): List<StoredAddon> =
        decode(context.addonStore.data.first()[KEY])

    private suspend fun writeStored(list: List<StoredAddon>) {
        val encoded = json.encodeToString(listSerializer, list)
        context.addonStore.edit { it[KEY] = encoded }
    }

    private fun decode(raw: String?): List<StoredAddon> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(listSerializer, raw) }.getOrDefault(emptyList())
    }

    /** Fetch a catalog's items and map them to domain [MediaItem]s. */
    suspend fun catalog(addon: Addon, def: AddonCatalogDef): List<MediaItem> = try {
        val url = "${addon.base}catalog/${def.type}/${def.id}.json"
        val items = api.catalog(url).metas.map { it.toMediaItem() }
        enrichPosters(items)
    } catch (t: Throwable) {
        Log.w(TAG, "Catalog fetch failed for ${addon.manifest.name}/${def.id}", t)
        emptyList()
    }

    /**
     * Search Cinemeta's movie & series catalogs. This gives Search (and Home tiles,
     * which open Search) real results even when TMDB is unavailable — TMDB is only
     * one of the search sources, not the only one.
     */
    suspend fun searchCatalogs(query: String): List<MediaItem> = coroutineScope {
        val q = query.trim()
        if (q.length < 2) return@coroutineScope emptyList()
        val encoded = java.net.URLEncoder.encode(q, "UTF-8")
        listOf("movie", "series").map { type ->
            async {
                runCatching {
                    val url = "${AddonApi.CINEMETA_BASE}catalog/$type/top/search=$encoded.json"
                    api.catalog(url).metas.map { it.toMediaItem() }
                }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten().distinctBy { it.id }
    }

    /**
     * Some catalog addons return "thin" items (id/type only). When posters are
     * missing, fill title/poster/etc. from Cinemeta (IMDb) so cards render.
     */
    private suspend fun enrichPosters(items: List<MediaItem>): List<MediaItem> = coroutineScope {
        if (items.none { it.posterUrl == null }) return@coroutineScope items
        val head = items.take(ENRICH_LIMIT)
        val enriched = head.map { item ->
            async {
                val imdb = item.imdbId
                if (item.posterUrl != null || imdb == null) return@async item
                val type = if (item.type == MediaType.TV_SHOW) "series" else "movie"
                val meta = runCatching {
                    api.meta("${AddonApi.CINEMETA_BASE}meta/$type/$imdb.json").meta
                }.getOrNull() ?: return@async item
                item.copy(
                    title = item.title.ifBlank { meta.name },
                    overview = item.overview.ifBlank { meta.description },
                    posterUrl = meta.poster,
                    backdropUrl = item.backdropUrl ?: meta.background,
                    year = item.year ?: meta.releaseInfo?.take(4)?.toIntOrNull(),
                    rating = item.rating ?: meta.imdbRating?.toDoubleOrNull(),
                )
            }
        }.awaitAll()
        enriched + items.drop(ENRICH_LIMIT)
    }

    /**
     * Look up full metadata for a Stremio id: prefer an installed meta addon,
     * then fall back to Cinemeta (the standard IMDb-based provider).
     */
    suspend fun meta(type: String, stremioId: String): AddonMeta? {
        val installed = current().filter { it.providesMeta && type in it.manifest.types }
        for (addon in installed) {
            runCatching { api.meta("${addon.base}meta/$type/$stremioId.json").meta }
                .getOrNull()?.let { return it }
        }
        return runCatching {
            api.meta("${AddonApi.CINEMETA_BASE}meta/$type/$stremioId.json").meta
        }.getOrNull()
    }

    companion object {
        private const val TAG = "AddonRepository"
        private const val ENRICH_LIMIT = 20
        private val KEY = stringPreferencesKey("installed_addons_json")
    }
}

/** Maps a Stremio meta object into the app's domain model. */
fun AddonMeta.toMediaItem(): MediaItem {
    val mediaType = if (type == "series" || type == "tv") MediaType.TV_SHOW else MediaType.MOVIE
    val stremType = if (mediaType == MediaType.TV_SHOW) "series" else "movie"
    val imdb = IMDB_REGEX.find(id)?.value
    return MediaItem(
        id = "stremio:$stremType:$id",
        type = mediaType,
        title = name,
        overview = description,
        posterUrl = poster,
        backdropUrl = background ?: poster,
        year = releaseInfo?.take(4)?.toIntOrNull(),
        rating = imdbRating?.toDoubleOrNull(),
        genres = genres,
        imdbId = imdb,
    )
}

private val IMDB_REGEX = Regex("tt\\d+")
