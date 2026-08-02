package com.dionysus.tv.data.addons

import android.util.Log
import com.dionysus.tv.core.model.DataResult
import com.dionysus.tv.core.model.MediaItem
import com.dionysus.tv.core.model.MediaType
import com.dionysus.tv.data.local.dao.AddonDao
import com.dionysus.tv.data.local.entity.InstalledAddonEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** An installed Stremio addon plus the base URL its resources are served from. */
data class Addon(
    val transportUrl: String,
    val manifest: AddonManifest,
) {
    /** Everything up to and including the last '/' before manifest.json. */
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

@Singleton
class AddonRepository @Inject constructor(
    private val api: AddonApi,
    private val dao: AddonDao,
    private val json: Json,
) {
    val installedAddons: Flow<List<Addon>> = dao.observeAll().map { list ->
        list.mapNotNull { entity ->
            runCatching {
                Addon(entity.transportUrl, json.decodeFromString(AddonManifest.serializer(), entity.manifestJson))
            }.getOrNull()
        }
    }

    suspend fun current(): List<Addon> = installedAddons.first()

    /** Fetch a manifest from [manifestUrl] and persist the addon. */
    suspend fun install(manifestUrl: String): DataResult<Addon> = DataResult.catching {
        val url = manifestUrl.trim()
        val manifest = api.manifest(url)
        val position = dao.maxPosition() + 1
        dao.insert(
            InstalledAddonEntity(
                transportUrl = url,
                name = manifest.name,
                manifestJson = json.encodeToString(AddonManifest.serializer(), manifest),
                position = position,
                installedAt = AddonClock.now(),
            ),
        )
        Addon(url, manifest)
    }

    suspend fun remove(transportUrl: String) = dao.remove(transportUrl)

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
    }
}

/** Tiny indirection so the repository stays unit-testable without a clock dep. */
object AddonClock {
    fun now(): Long = System.currentTimeMillis()
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
