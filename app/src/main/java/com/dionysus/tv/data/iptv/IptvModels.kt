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
data class Channel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logo: String? = null,
    val group: String = "General",
    val epgId: String? = null,
    val playlistId: String,
    val playlistName: String,
)

/** One EPG entry (a programme airing on a channel). */
data class Programme(
    val epgId: String,
    val startMs: Long,
    val stopMs: Long,
    val title: String,
    val description: String = "",
)

/** The now/next pair used to annotate channel cards and the guide. */
data class NowNext(
    val now: Programme? = null,
    val next: Programme? = null,
)
