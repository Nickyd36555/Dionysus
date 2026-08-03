package com.dionysus.tv.data.iptv

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dionysus.tv.core.model.DataResult
import com.dionysus.tv.core.model.MediaItem
import com.dionysus.tv.core.model.MediaSource
import com.dionysus.tv.core.model.MediaType
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
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
    private val clockFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    // In-memory caches so unified search and the guide don't re-hit the network
    // on every keystroke. Invalidated whenever playlists change.
    private var cachedChannels: List<Channel> = emptyList()
    private var cachedVod: List<MediaItem> = emptyList()
    private var cachedEpg: Map<String, List<Programme>> = emptyMap()
    private var loaded = false

    val playlists: Flow<List<StoredPlaylist>> = context.iptvStore.data.map { prefs ->
        decode(prefs[KEY_PLAYLISTS])
    }

    val favorites: Flow<Set<String>> = context.iptvStore.data.map { prefs ->
        prefs[KEY_FAVORITES] ?: emptySet()
    }

    /** Category groups the user has chosen to hide from the Live TV rail. */
    val hiddenGroups: Flow<Set<String>> = context.iptvStore.data.map { prefs ->
        prefs[KEY_HIDDEN_GROUPS] ?: emptySet()
    }

    suspend fun toggleHiddenGroup(group: String) {
        context.iptvStore.edit { prefs ->
            val set = (prefs[KEY_HIDDEN_GROUPS] ?: emptySet()).toMutableSet()
            if (!set.add(group)) set.remove(group)
            prefs[KEY_HIDDEN_GROUPS] = set
        }
    }

    suspend fun clearHiddenGroups() {
        context.iptvStore.edit { it.remove(KEY_HIDDEN_GROUPS) }
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
        invalidateCache()
    }

    /** Drop cached channels/VOD/EPG so the next load re-fetches. */
    fun invalidateCache() {
        loaded = false
        cachedChannels = emptyList()
        cachedVod = emptyList()
        cachedEpg = emptyMap()
    }

    suspend fun toggleFavorite(channelId: String) {
        context.iptvStore.edit { prefs ->
            val set = (prefs[KEY_FAVORITES] ?: emptySet()).toMutableSet()
            if (!set.add(channelId)) set.remove(channelId)
            prefs[KEY_FAVORITES] = set
        }
    }

    /** Load channels from every configured playlist, concurrently (cached). */
    suspend fun loadChannels(): DataResult<List<Channel>> = DataResult.catching {
        ensureLoaded()
        cachedChannels
    }

    /** Load VOD (movies) from every Xtream playlist, as playable [MediaItem]s. */
    suspend fun loadVod(): DataResult<List<MediaItem>> = DataResult.catching {
        ensureLoaded()
        cachedVod
    }

    /**
     * Unified content search across VOD and Live TV (EPG programme titles).
     * Each result is tagged with its [MediaSource] and carries a direct stream
     * URL so it plays without scraping. Catalog (Dionysus) results come from the
     * metadata repository separately.
     */
    suspend fun searchContent(query: String): List<MediaItem> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        ensureLoaded()
        val results = ArrayList<MediaItem>()
        // VOD movies whose title matches.
        cachedVod.filter { it.title.lowercase().contains(q) }.take(30).forEach { results.add(it) }
        // Things airing on Live TV: match EPG programme titles, resolve the channel.
        val channelsByEpg = cachedChannels.filter { it.epgId != null }.associateBy { normEpgId(it.epgId!!) }
        val now = System.currentTimeMillis()
        cachedEpg.forEach { (epgId, programmes) ->
            val channel = channelsByEpg[epgId] ?: return@forEach
            programmes.filter { it.title.lowercase().contains(q) && it.stopMs > now }
                .sortedBy { it.startMs }
                .distinctBy { it.title }
                .take(3)
                .forEach { prog ->
                    val schedule = "${clockFormat.format(Date(prog.startMs))}–${clockFormat.format(Date(prog.stopMs))}"
                    val airing = now in prog.startMs until prog.stopMs
                    val subtitle = (if (airing) "● NOW  " else "") + "${channel.name}  ·  $schedule"
                    results.add(
                        MediaItem(
                            id = "livetv:${channel.id}:${prog.startMs}",
                            type = MediaType.MOVIE,
                            title = prog.title,
                            subtitle = subtitle,
                            overview = prog.description,
                            posterUrl = channel.logo,
                            genres = listOf(channel.name),
                            source = MediaSource.LIVE_TV,
                            streamUrl = channel.streamUrl,
                        ),
                    )
                }
        }
        return results
    }

    /** Populate the in-memory caches once (channels, VOD, EPG). */
    private suspend fun ensureLoaded() {
        if (loaded) return
        val defs = runCatching { playlists.first() }.getOrDefault(emptyList())
        if (defs.isEmpty()) { loaded = true; return }
        cachedChannels = runCatching { fetchAllChannels(defs) }.getOrDefault(emptyList())
        cachedVod = runCatching { fetchAllVod(defs) }.getOrDefault(emptyList())
        cachedEpg = runCatching { loadEpg() }.getOrDefault(emptyMap())
        loaded = true
    }

    private suspend fun fetchAllChannels(defs: List<StoredPlaylist>): List<Channel> = coroutineScope {
        defs.map { pl -> async { runCatching { channelsFor(pl) }.getOrElse { emptyList() } } }
            .awaitAll()
            .flatten()
    }

    private suspend fun fetchAllVod(defs: List<StoredPlaylist>): List<MediaItem> = coroutineScope {
        defs.filter { it.kind == PlaylistKind.XTREAM }
            .map { pl -> async { runCatching { loadXtreamVod(pl) }.getOrElse { emptyList() } } }
            .awaitAll()
            .flatten()
    }

    private suspend fun loadXtreamVod(pl: StoredPlaylist): List<MediaItem> {
        val catsUrl = "${pl.host}/player_api.php?username=${pl.username}&password=${pl.password}&action=get_vod_categories"
        val streamsUrl = "${pl.host}/player_api.php?username=${pl.username}&password=${pl.password}&action=get_vod_streams"
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
            val ext = o.str("container_extension").ifBlank { "mp4" }
            val category = categories[o.str("category_id")]?.takeIf { it.isNotBlank() } ?: "General"
            MediaItem(
                id = "vod:${pl.id}:$streamId",
                type = MediaType.MOVIE,
                title = o.str("name").ifBlank { "Movie $streamId" },
                posterUrl = o.str("stream_icon").takeIf { it.isNotBlank() },
                rating = o.str("rating").toDoubleOrNull(),
                // Category name is carried in genres so the UI can group VOD by it.
                genres = listOf(category),
                source = MediaSource.VOD,
                streamUrl = "${pl.host}/movie/${pl.username}/${pl.password}/$streamId.$ext",
            )
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

    // XMLTV files (esp. full Xtream guides for 1000s of channels) are big and slow;
    // the shared 60s call timeout would kill them mid-download. This client removes
    // the overall call timeout and allows a long read.
    private val epgClient: OkHttpClient by lazy {
        client.newBuilder()
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .readTimeout(4, TimeUnit.MINUTES)
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** Fetch and merge EPG for every playlist that declares an XMLTV URL. */
    suspend fun loadEpg(): Map<String, List<Programme>> = coroutineScope {
        val defs = runCatching { playlists.first() }.getOrDefault(emptyList())
        val maps = defs.filter { it.epgUrl.isNotBlank() }.map { pl ->
            async { runCatching { fetchEpg(pl.epgUrl) }.getOrDefault(emptyMap()) }
        }.awaitAll()
        val merged = HashMap<String, List<Programme>>()
        // Normalize channel ids so Xtream epg_channel_id matches XMLTV channel ids
        // regardless of case/whitespace differences between the two endpoints.
        maps.forEach { m -> m.forEach { (k, v) -> val key = normEpgId(k); merged[key] = (merged[key].orEmpty() + v) } }
        merged
    }

    private suspend fun fetchEpg(url: String): Map<String, List<Programme>> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept-Encoding", "gzip").build()
        epgClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@use emptyMap<String, List<Programme>>()
            val bytes = resp.body ?: return@use emptyMap<String, List<Programme>>()
            val stream = bytes.byteStream()
            // Handle .gz URLs (OkHttp already transparently gunzips Content-Encoding).
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
        invalidateCache()
    }

    private suspend fun writePlaylists(list: List<StoredPlaylist>) {
        val encoded = json.encodeToString(listSerializer, list)
        context.iptvStore.edit { it[KEY_PLAYLISTS] = encoded }
    }

    private fun decode(raw: String?): List<StoredPlaylist> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(listSerializer, raw) }.getOrDefault(emptyList())
    }

    /** Normalize an EPG channel id for matching across endpoints (case/whitespace). */
    fun normEpgId(id: String): String = id.trim().lowercase()

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
        private val KEY_HIDDEN_GROUPS = stringSetPreferencesKey("iptv_hidden_groups")
    }
}
