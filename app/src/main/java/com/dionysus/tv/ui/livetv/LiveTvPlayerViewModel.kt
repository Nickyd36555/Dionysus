package com.dionysus.tv.ui.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dionysus.tv.data.iptv.Channel
import com.dionysus.tv.data.iptv.IptvRepository
import com.dionysus.tv.data.iptv.LiveSession
import com.dionysus.tv.data.iptv.NowNext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveTvPlayerViewModel @Inject constructor(
    private val session: LiveSession,
    private val iptv: IptvRepository,
) : ViewModel() {

    val channels: List<Channel> = session.channels
    val startIndex: Int = channels.indexOfFirst { it.id == session.startId }.coerceAtLeast(0)

    fun nowNext(channel: Channel): NowNext = session.nowNext(channel)

    fun nowMs(): Long = System.currentTimeMillis() + session.nowOffsetMs

    /** Remember which channel is showing so the guide can restore focus to it. */
    fun markFocused(channel: Channel) {
        session.lastFocusedId = channel.id
    }

    /** Fetch per-channel EPG on demand if this channel has none yet. */
    fun ensureEpg(channel: Channel) {
        if (session.programmesFor(channel).isNotEmpty()) return
        if (channel.xtreamStreamId == null) return
        viewModelScope.launch {
            val progs = iptv.shortEpg(channel)
            if (progs.isNotEmpty()) session.shortEpg[channel.id] = progs
        }
    }
}
