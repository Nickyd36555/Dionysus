package com.dionysus.tv.data.iptv

import kotlinx.serialization.Serializable

/** How a playlist is sourced. */
object PlaylistKind {
    const val M3U = "m3u"
    const val XTREAM = "xtream"
}

/**
 * A persisted IPTV playlist. Either a plain M3U/M3U8 URL, or Xtream Codes
 * credentials that we expand into channels via the provider's `player_api.php`.
 */
@Serializable
data class StoredPlaylist(
    val id: String,
    val name: String,
    val kind: String,
    val url: String = "",
    val host: String = "",
    val username: String = "",
    val password: String = "",
    val epgUrl: String = "",
)

/** A single live channel resolved from a playlist, ready to play. */
@Serializable
data class Channel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logo: String? = null,
    val group: String = "General",
    val epgId: String? = null,
    val playlistId: String,
    val playlistName: String,
    /** Xtream stream id, used to fetch per-channel EPG via get_short_epg. */
    val xtreamStreamId: String? = null,
)

/** One EPG entry (a programme airing on a channel). */
@Serializable
data class Programme(
    val epgId: String,
    val startMs: Long,
    val stopMs: Long,
    val title: String,
    val description: String = "",
)

/** Persisted, parsed EPG so the guide loads instantly on the next launch. */
@Serializable
data class EpgCache(
    val savedAtMs: Long,
    val programmes: Map<String, List<Programme>>,
)

/** Persisted channels + VOD so Live TV paints instantly, then refreshes. */
@Serializable
data class ContentCache(
    val savedAtMs: Long,
    val channels: List<Channel>,
    val vod: List<com.dionysus.tv.core.model.MediaItem>,
)

/** The now/next pair used to annotate channel cards and the guide. */
data class NowNext(
    val now: Programme? = null,
    val next: Programme? = null,
)

/**
 * IPTV providers name categories like "US | Entertainment", "UK | Sports", or
 * "24/7 - Breaking Bad". These helpers bundle them into a top-level parent group
 * (US, UK, SPORTS, 24/7, …) that the UI can drill into.
 */
object CategoryGrouping {
    private val DELIMS = listOf("|", ":", "»", "•", " - ", " – ", " — ")
    private val H24 = Regex("(?i)24\\s*[/-]?\\s*7")

    /** The parent group for a raw category name (upper-cased for stable bundling). */
    fun group(raw: String): String {
        val c = raw.trim()
        if (H24.containsMatchIn(c)) return "24/7"
        for (d in DELIMS) {
            val i = c.indexOf(d)
            if (i > 0) return c.substring(0, i).trim().uppercase()
        }
        return c
    }

    /** The sub-category label within a group (the part after the delimiter). */
    fun sub(raw: String): String {
        val c = raw.trim()
        for (d in DELIMS) {
            val i = c.indexOf(d)
            if (i >= 0) return c.substring(i + d.length).trim().ifEmpty { c }
        }
        return c
    }
}
