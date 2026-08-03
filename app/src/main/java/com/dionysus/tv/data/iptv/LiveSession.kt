package com.dionysus.tv.data.iptv

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hand-off between the Live TV guide and the full-screen live player. The guide
 * populates the ordered channel list (plus the EPG it already has) right before
 * navigating, so the player can do channel up/down and show now/next synopses
 * without re-loading everything or passing large data through nav arguments.
 */
@Singleton
class LiveSession @Inject constructor() {
    var channels: List<Channel> = emptyList()
    var startId: String? = null
    var epg: Map<String, List<Programme>> = emptyMap()
    var shortEpg: MutableMap<String, List<Programme>> = mutableMapOf()
    var nowOffsetMs: Long = 0L

    /** The channel most recently opened, so the guide can restore focus to it. */
    var lastFocusedId: String? = null

    fun programmesFor(channel: Channel): List<Programme> {
        val fromXmltv = channel.epgId?.let { epg[normEpgId(it)] }.orEmpty()
        return fromXmltv.ifEmpty { shortEpg[channel.id].orEmpty() }
    }

    fun nowNext(channel: Channel): NowNext {
        val list = programmesFor(channel)
        if (list.isEmpty()) return NowNext()
        val now = System.currentTimeMillis() + nowOffsetMs
        val current = list.firstOrNull { now in it.startMs until it.stopMs }
        val next = list.firstOrNull { it.startMs >= now }
        return NowNext(current, next)
    }

    private fun normEpgId(id: String): String = id.trim().lowercase()
}
