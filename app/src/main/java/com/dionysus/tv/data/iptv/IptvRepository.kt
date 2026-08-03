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
import java.io.File
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
        epgUrl: String = "",
    ): DataResult<Unit> = DataResult.catching {
        val base = normalizeHost(host)
        require(base.startsWith("http")) { "Enter a valid Xtream server URL (http://host:port)." }
        // Verify credentials before saving.
        val authUrl = "$base/player_api.php?username=${username.trim()}&password=${password.trim()}"
        val body = fetchText(authUrl)
        val auth = runCatching { json.parseToJsonElement(body).jsonObjectOrNull()?.get("user_info") }.getOrNull()
        require(auth != null) { "Server didn't accept those Xtream credentials." }
        // Use a custom EPG URL if provided, otherwise the provider's own xmltv.php.
        val epg = epgUrl.trim().ifBlank {
            "$base/xmltv.php?username=${username.trim()}&password=${password.trim()}"
        }
        val playlist = StoredPlaylist(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Xtream" },
            kind = PlaylistKind.XTREAM,
            host = base,
            username = username.trim(),
            password = password.trim(),
            epgUrl = epg,
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
        runCatching { if (epgCacheFile.exists()) epgCacheFile.delete() }
        runCatching { if (contentCacheFile.exists()) contentCacheFile.delete() }
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

    private val contentCacheFile: File by lazy { File(context.filesDir, "iptv_content.json") }

    /** Instant channels + VOD for first paint: memory, then disk (any age), no network. */
    suspend fun cachedContentOrDisk(): Pair<List<Channel>, List<MediaItem>> = withContext(Dispatchers.IO) {
        if (cachedChannels.isNotEmpty() || cachedVod.isNotEmpty()) return@withContext cachedChannels to cachedVod
        readContentDisk()?.let { cachedChannels = it.channels; cachedVod = it.vod }
        cachedChannels to cachedVod
    }

    /** Force a fresh network load of channels + VOD, updating memory and disk. */
    suspend fun refreshContent(): Pair<List<Channel>, List<MediaItem>> {
        val defs = runCatching { playlists.first() }.getOrDefault(emptyList())
        if (defs.isEmpty()) {
            cachedChannels = emptyList(); cachedVod = emptyList(); loaded = true
            return emptyList<Channel>() to emptyList()
        }
        val ch = runCatching { fetchAllChannels(defs) }.getOrDefault(emptyList())
        val vod = runCatching { fetchAllVod(defs) }.getOrDefault(emptyList())
        if (ch.isNotEmpty() || vod.isNotEmpty()) {
            cachedChannels = ch
            cachedVod = vod
            writeContentDisk(ch, vod)
        }
        loaded = true
        return cachedChannels to cachedVod
    }

    /** Populate the in-memory caches (channels, VOD): memory → disk → network. */
    private suspend fun ensureLoaded() {
        if (loaded && cachedChannels.isNotEmpty()) return
        if (cachedChannels.isEmpty() && cachedVod.isEmpty()) {
            withContext(Dispatchers.IO) { readContentDisk() }?.let {
                cachedChannels = it.channels; cachedVod = it.vod
            }
        }
        if (cachedChannels.isEmpty() && cachedVod.isEmpty()) {
            refreshContent()
        } else {
            loaded = true
        }
        if (cachedEpg.isEmpty()) cachedEpg = runCatching { loadEpg() }.getOrDefault(emptyMap())
    }

    private fun readContentDisk(): ContentCache? = runCatching {
        if (!contentCacheFile.exists()) return null
        json.decodeFromString(ContentCache.serializer(), contentCacheFile.readText())
    }.getOrNull()

    private fun writeContentDisk(channels: List<Channel>, vod: List<MediaItem>) {
        runCatching {
            val cache = ContentCache(System.currentTimeMillis(), channels, vod)
            contentCacheFile.writeText(json.encodeToString(ContentCache.serializer(), cache))
        }
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
                xtreamStreamId = streamId,
            )
        }
    }

    /**
     * Per-channel EPG via Xtream's `get_short_epg` — the reliable source when a
     * provider's global xmltv.php is empty or disabled. Titles/descriptions are
     * base64-encoded; timestamps are unix seconds.
     */
    suspend fun shortEpg(channel: Channel, limit: Int = 24): List<Programme> {
        val streamId = channel.xtreamStreamId ?: return emptyList()
        val pl = runCatching { playlists.first() }.getOrDefault(emptyList())
            .firstOrNull { it.id == channel.playlistId && it.kind == PlaylistKind.XTREAM } ?: return emptyList()
        val url = "${pl.host}/player_api.php?username=${pl.username}&password=${pl.password}" +
            "&action=get_short_epg&stream_id=$streamId&limit=$limit"
        return runCatching {
            val root = json.parseToJsonElement(fetchText(url)) as? JsonObject ?: return emptyList()
            val listings = root["epg_listings"] as? JsonArray ?: return emptyList()
            listings.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val start = o.str("start_timestamp").toLongOrNull()?.times(1000)
                    ?: parseXtreamDate(o.str("start")) ?: return@mapNotNull null
                val stop = o.str("stop_timestamp").toLongOrNull()?.times(1000)
                    ?: parseXtreamDate(o.str("end")) ?: (start + 1_800_000)
                val title = decodeB64(o.str("title")).ifBlank { return@mapNotNull null }
                Programme(
                    epgId = channel.id,
                    startMs = start,
                    stopMs = stop,
                    title = title,
                    description = decodeB64(o.str("description")).take(220),
                )
            }.sortedBy { it.startMs }
        }.getOrDefault(emptyList())
    }

    private val xtreamDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private fun parseXtreamDate(value: String): Long? =
        if (value.isBlank()) null else runCatching { xtreamDateFormat.parse(value)?.time }.getOrNull()

    private fun decodeB64(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            String(android.util.Base64.decode(value, android.util.Base64.DEFAULT)).trim()
        }.getOrDefault(value)
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

    /** In-memory + on-disk EPG so the guide is instant on relaunch (TiViMate-style). */
    private val epgCacheFile: File by lazy { File(context.filesDir, "epg_cache.json") }

    /** Instant EPG for first paint: memory, then disk (any age), no network. */
    suspend fun cachedEpgOrDisk(): Map<String, List<Programme>> = withContext(Dispatchers.IO) {
        if (cachedEpg.isNotEmpty()) return@withContext cachedEpg
        readEpgDisk()?.let { cachedEpg = it.programmes }
        cachedEpg
    }

    /**
     * The fast path: download the provider's whole XMLTV **once** (a single
     * request, like TiViMate), parse it, and cache to disk. Uses fresh disk cache
     * when available; falls back to stale cache if the network fetch fails.
     */
    suspend fun loadEpg(): Map<String, List<Programme>> = withContext(Dispatchers.IO) {
        val disk = readEpgDisk()
        val fresh = disk != null && (System.currentTimeMillis() - disk.savedAtMs) < EPG_TTL_MS
        if (fresh) {
            cachedEpg = disk!!.programmes
            return@withContext cachedEpg
        }
        val merged = runCatching { fetchBulkEpg() }.getOrDefault(emptyMap())
        when {
            merged.isNotEmpty() -> {
                cachedEpg = merged
                writeEpgDisk(merged)
            }
            disk != null -> cachedEpg = disk.programmes // keep showing stale rather than nothing
        }
        cachedEpg
    }

    private suspend fun fetchBulkEpg(): Map<String, List<Programme>> = coroutineScope {
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

    private fun readEpgDisk(): EpgCache? = runCatching {
        if (!epgCacheFile.exists()) return null
        json.decodeFromString(EpgCache.serializer(), epgCacheFile.readText())
    }.getOrNull()

    private fun writeEpgDisk(epg: Map<String, List<Programme>>) {
        runCatching {
            val cache = EpgCache(savedAtMs = System.currentTimeMillis(), programmes = epg)
            epgCacheFile.writeText(json.encodeToString(EpgCache.serializer(), cache))
        }
    }

    private suspend fun fetchEpg(url: String): Map<String, List<Programme>> = withContext(Dispatchers.IO) {
        // NOTE: do NOT set Accept-Encoding manually — that turns off OkHttp's
        // transparent gzip and hands us raw compressed bytes. Instead we sniff the
        // gzip magic bytes ourselves so both .gz files and gzipped xmltv.php work.
        val request = Request.Builder().url(url)
            .header("User-Agent", IPTV_USER_AGENT)
            .build()
        epgClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@use emptyMap<String, List<Programme>>()
            val body = resp.body ?: return@use emptyMap<String, List<Programme>>()
            val buffered = java.io.BufferedInputStream(body.byteStream())
            buffered.mark(2)
            val b0 = buffered.read()
            val b1 = buffered.read()
            buffered.reset()
            val isGzip = b0 == 0x1f && b1 == 0x8b
            val decoded = if (isGzip) GZIPInputStream(buffered) else buffered
            decoded.use { EpgParser.parse(it) }
        }
    }

    private suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        // Some IPTV panels reject non-media-player User-Agents (serving empty data),
        // so identify as a player like TiViMate/VLC do.
        val request = Request.Builder().url(url).header("User-Agent", IPTV_USER_AGENT).build()
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

    /**
     * Difference between the Xtream provider's clock and this device's clock, in
     * ms (serverNow − deviceNow). Adding it to the device time yields the correct
     * "now" even when the device clock/timezone is wrong. Null if unavailable.
     */
    suspend fun serverTimeOffsetMs(): Long? {
        val pl = runCatching { playlists.first() }.getOrDefault(emptyList())
            .firstOrNull { it.kind == PlaylistKind.XTREAM } ?: return null
        val url = "${pl.host}/player_api.php?username=${pl.username}&password=${pl.password}"
        return runCatching {
            val root = json.parseToJsonElement(fetchText(url)) as? JsonObject ?: return null
            val server = root["server_info"] as? JsonObject ?: return null
            val ts = server.str("timestamp_now").toLongOrNull() ?: return null
            ts * 1000 - System.currentTimeMillis()
        }.getOrNull()
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
        private const val EPG_TTL_MS = 12 * 60 * 60 * 1000L // refresh bulk EPG every 12h
        // Identify as a media player; some panels only serve EPG to known UAs.
        private const val IPTV_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"
        private val KEY_PLAYLISTS = stringPreferencesKey("iptv_playlists_json")
        private val KEY_FAVORITES = stringSetPreferencesKey("iptv_favorites")
        private val KEY_HIDDEN_GROUPS = stringSetPreferencesKey("iptv_hidden_groups")
    }
}
