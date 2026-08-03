package com.dionysus.tv.data.iptv

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dionysus.tv.core.model.DataResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

private val Context.iptvStore by preferencesDataStore(name = "dionysus_iptv")

/**
 * Owns IPTV playlists (persisted in DataStore) and turns them into playable
 * channels. Supports plain M3U/M3U8 URLs and Xtream Codes accounts, plus
 * best-effort XMLTV EPG so the guide can show what's on now/next.
 */
@Singleton
class IptvRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val json: Json,
) {
    private val listSerializer = ListSerializer(StoredPlaylist.serializer())

    val playlists: Flow<List<StoredPlaylist>> = context.iptvStore.data.map { prefs ->
        decode(prefs[KEY_PLAYLISTS])
    }

    val favorites: Flow<Set<String>> = context.iptvStore.data.map { prefs ->
        prefs[KEY_FAVORITES] ?: emptySet()
    }

    suspend fun addM3u(name: String, url: String, epgUrl: String): DataResult<Unit> = DataResult.catching {
        val cleanUrl = url.trim()
        require(cleanUrl.startsWith("http")) { "Enter a valid http(s) playlist URL." }
        // Validate by fetching once so bad URLs fail here, not silently later.
        fetchText(cleanUrl)
        val playlist = StoredPlaylist(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Playlist" },
            kind = PlaylistKind.M3U,
            url = cleanUrl,
            epgUrl = epgUrl.trim(),
        )
        savePlaylist(playlist)
    }

    suspend fun addXtream(
        name: String,
        host: String,
        username: String,
        password: String,
    ): DataResult<Unit> = DataResult.catching {
        val base = normalizeHost(host)
        require(base.startsWith("http")) { "Enter a valid Xtream server URL (http://host:port)." }
        // Verify credentials before saving.
        val authUrl = "$base/player_api.php?username=${username.trim()}&password=${password.trim()}"
        val body = fetchText(authUrl)
        val auth = runCatching { json.parseToJsonElement(body).jsonObjectOrNull()?.get("user_info") }.getOrNull()
        require(auth != null) { "Server didn't accept those Xtream credentials." }
        val playlist = StoredPlaylist(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Xtream" },
            kind = PlaylistKind.XTREAM,
            host = base,
            username = username.trim(),
            password = password.trim(),
            epgUrl = "$base/xmltv.php?username=${username.trim()}&password=${password.trim()}",
        )
        savePlaylist(playlist)
    }

    suspend fun remove(id: String) {
        val current = decode(context.iptvStore.data.first()[KEY_PLAYLISTS])
        writePlaylists(current.filterNot { it.id == id })
    }

    suspend fun toggleFavorite(channelId: String) {
        context.iptvStore.edit { prefs ->
            val set = (prefs[KEY_FAVORITES] ?: emptySet()).toMutableSet()
            if (!set.add(channelId)) set.remove(channelId)
            prefs[KEY_FAVORITES] = set
        }
    }

    /** Load channels from every configured playlist, concurrently. */
    suspend fun loadChannels(): DataResult<List<Channel>> = DataResult.catching {
        val defs = playlists.first()
        if (defs.isEmpty()) return@catching emptyList()
        coroutineScope {
            defs.map { pl -> async { runCatching { channelsFor(pl) }.getOrElse { emptyList() } } }
                .awaitAll()
                .flatten()
        }
    }

    private suspend fun channelsFor(pl: StoredPlaylist): List<Channel> = when (pl.kind) {
        PlaylistKind.XTREAM -> loadXtreamChannels(pl)
        else -> M3uParser.parse(fetchText(pl.url), pl.id, pl.name)
    }

    private suspend fun loadXtreamChannels(pl: StoredPlaylist): List<Channel> {
        val catsUrl = "${pl.host}/player_api.php?username=${pl.username}&password=${pl.password}&action=get_live_categories"
        val streamsUrl = "${pl.host}/player_api.php?username=${pl.username}&password=${pl.password}&action=get_live_streams"
        val categories = runCatching {
            (json.parseToJsonElement(fetchText(catsUrl)) as? JsonArray)?.associate {
                val o = it as JsonObject
                o.str("category_id") to o.str("category_name")
            }.orEmpty()
        }.getOrDefault(emptyMap())

        val streams = json.parseToJsonElement(fetchText(streamsUrl)) as? JsonArray ?: return emptyList()
        return streams.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val streamId = o.str("stream_id").ifBlank { return@mapNotNull null }
            val ext = "ts"
            Channel(
                id = "${pl.id}|$streamId",
                name = o.str("name").ifBlank { "Channel $streamId" },
                streamUrl = "${pl.host}/live/${pl.username}/${pl.password}/$streamId.$ext",
                logo = o.str("stream_icon").takeIf { it.isNotBlank() },
                group = categories[o.str("category_id")]?.takeIf { it.isNotBlank() } ?: "General",
                epgId = o.str("epg_channel_id").takeIf { it.isNotBlank() },
                playlistId = pl.id,
                playlistName = pl.name,
            )
        }
    }

    /** Fetch and merge EPG for every playlist that declares an XMLTV URL. */
    suspend fun loadEpg(): Map<String, List<Programme>> = coroutineScope {
        val defs = runCatching { playlists.first() }.getOrDefault(emptyList())
        val maps = defs.filter { it.epgUrl.isNotBlank() }.map { pl ->
            async { runCatching { fetchEpg(pl.epgUrl) }.getOrDefault(emptyMap()) }
        }.awaitAll()
        val merged = HashMap<String, List<Programme>>()
        maps.forEach { m -> m.forEach { (k, v) -> merged[k] = (merged[k].orEmpty() + v) } }
        merged
    }

    private suspend fun fetchEpg(url: String): Map<String, List<Programme>> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@use emptyMap<String, List<Programme>>()
            val bytes = resp.body ?: return@use emptyMap<String, List<Programme>>()
            val stream = bytes.byteStream()
            val decoded = if (url.endsWith(".gz", ignoreCase = true)) GZIPInputStream(stream) else stream
            decoded.use { EpgParser.parse(it) }
        }
    }

    private suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
    }

    private suspend fun savePlaylist(playlist: StoredPlaylist) {
        val current = decode(context.iptvStore.data.first()[KEY_PLAYLISTS]).toMutableList()
        current.add(playlist)
        writePlaylists(current)
    }

    private suspend fun writePlaylists(list: List<StoredPlaylist>) {
        val encoded = json.encodeToString(listSerializer, list)
        context.iptvStore.edit { it[KEY_PLAYLISTS] = encoded }
    }

    private fun decode(raw: String?): List<StoredPlaylist> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(listSerializer, raw) }.getOrDefault(emptyList())
    }

    private fun normalizeHost(host: String): String {
        var h = host.trim().trimEnd('/')
        if (!h.startsWith("http")) h = "http://$h"
        return h
    }

    private fun JsonObject.str(key: String): String =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull().orEmpty().let {
            if (it == "null") "" else it
        }

    private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull(): JsonObject? =
        this as? JsonObject

    companion object {
        private const val TAG = "IptvRepository"
        private val KEY_PLAYLISTS = stringPreferencesKey("iptv_playlists_json")
        private val KEY_FAVORITES = stringSetPreferencesKey("iptv_favorites")
    }
}
