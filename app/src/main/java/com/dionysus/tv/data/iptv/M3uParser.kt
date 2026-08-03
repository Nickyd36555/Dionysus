package com.dionysus.tv.data.iptv

/**
 * Minimal, forgiving M3U/M3U8 playlist parser. Handles the extended tags IPTV
 * playlists use — `#EXTINF` with `tvg-id`, `tvg-logo`, `group-title`, plus the
 * `#EXTGRP` fallback group tag — and ignores VLC option lines it doesn't need.
 */
object M3uParser {

    private val ATTR_REGEX = Regex("([\\w-]+)=\"([^\"]*)\"")

    fun parse(content: String, playlistId: String, playlistName: String): List<Channel> {
        val channels = ArrayList<Channel>()
        var pending: Pending? = null
        var groupOverride: String? = null

        content.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() || line.startsWith("#EXTM3U") -> Unit

                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = ATTR_REGEX.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                    val name = line.substringAfterLast(',').trim()
                    pending = Pending(
                        name = name.ifBlank { attrs["tvg-name"].orEmpty() },
                        logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                        group = attrs["group-title"]?.takeIf { it.isNotBlank() },
                        epgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() },
                    )
                }

                line.startsWith("#EXTGRP", ignoreCase = true) ->
                    groupOverride = line.substringAfter(':').trim().takeIf { it.isNotBlank() }

                line.startsWith("#") -> Unit // #EXTVLCOPT and friends

                else -> {
                    val p = pending
                    if (p != null) {
                        channels += Channel(
                            id = "$playlistId|${p.epgId ?: line}",
                            name = p.name.ifBlank { "Channel ${channels.size + 1}" },
                            streamUrl = line,
                            logo = p.logo,
                            group = (p.group ?: groupOverride ?: "General"),
                            epgId = p.epgId,
                            playlistId = playlistId,
                            playlistName = playlistName,
                        )
                    }
                    pending = null
                    groupOverride = null
                }
            }
        }
        return channels
    }

    private data class Pending(
        val name: String,
        val logo: String?,
        val group: String?,
        val epgId: String?,
    )
}
