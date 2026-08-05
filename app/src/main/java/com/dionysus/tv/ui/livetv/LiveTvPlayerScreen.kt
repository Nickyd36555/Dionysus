@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.dionysus.tv.ui.livetv

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.dionysus.tv.data.iptv.Channel
import kotlinx.coroutines.delay
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Full-screen live TV player: video fills the screen, up/down zaps channels, OK
 * opens a channel list, and an auto-hiding info bar shows the current programme's
 * synopsis (now/next). Modeled on a set-top-box channel-surfing experience.
 */
@Composable
fun LiveTvPlayerScreen(
    onBack: () -> Unit,
    viewModel: LiveTvPlayerViewModel = hiltViewModel(),
) {
    val channels = viewModel.channels
    if (channels.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val context = LocalContext.current
    val rootFocus = remember { FocusRequester() }
    val listFocus = remember { FocusRequester() }

    // Quality (deinterlace/post-processing) is scaled to the device; hardware
    // decode with software fallback is always on. See PlaybackTuning.
    val tier = remember { com.dionysus.tv.player.PlaybackTuning.tier(context) }
    val libVlc = remember {
        LibVLC(context, com.dionysus.tv.player.PlaybackTuning.libVlcOptions(tier, live = true))
    }
    val player = remember { MediaPlayer(libVlc) }

    var index by remember { mutableIntStateOf(viewModel.startIndex) }
    var showInfo by remember { mutableStateOf(true) }
    var showList by remember { mutableStateOf(false) }
    var infoTick by remember { mutableIntStateOf(0) }
    var numberEntry by remember { mutableStateOf("") }
    val current = channels[index]

    // Number quick-zap: after a brief pause, jump to the typed channel number.
    LaunchedEffect(numberEntry) {
        if (numberEntry.isEmpty()) return@LaunchedEffect
        delay(1500)
        numberEntry.toIntOrNull()?.let { n ->
            if (n >= 1) index = (n - 1).coerceIn(0, channels.size - 1)
        }
        numberEntry = ""
    }

    DisposableEffect(Unit) {
        onDispose {
            player.stop()
            player.detachViews()
            player.release()
            libVlc.release()
        }
    }

    // (Re)load the stream whenever the channel changes.
    LaunchedEffect(index) {
        runCatching {
            val media = Media(libVlc, Uri.parse(current.streamUrl)).apply { setHWDecoderEnabled(true, false) }
            player.media = media
            media.release()
            player.play()
        }
        viewModel.markFocused(current)
        viewModel.ensureEpg(current)
        showInfo = true
        infoTick++
    }

    // Auto-hide the info bar a few seconds after the last zap.
    LaunchedEffect(infoTick, showList) {
        if (showList) return@LaunchedEffect
        showInfo = true
        delay(6_000)
        showInfo = false
    }

    LaunchedEffect(showList) {
        runCatching { if (showList) listFocus.requestFocus() else rootFocus.requestFocus() }
    }

    BackHandler {
        if (showList) showList = false else onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || showList) return@onKeyEvent false
                val digit = digitFor(event.key)
                when {
                    digit != null -> { numberEntry = (numberEntry + digit).take(4); true }
                    event.key == Key.DirectionUp || event.key == Key.ChannelUp -> {
                        index = (index - 1 + channels.size) % channels.size; true
                    }
                    event.key == Key.DirectionDown || event.key == Key.ChannelDown -> {
                        index = (index + 1) % channels.size; true
                    }
                    event.key == Key.DirectionCenter || event.key == Key.Enter -> { showList = true; true }
                    event.key == Key.DirectionLeft || event.key == Key.DirectionRight -> {
                        showInfo = true; infoTick++; true
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VLCVideoLayout(ctx).also { layout ->
                    layout.isFocusable = false
                    layout.isFocusableInTouchMode = false
                    layout.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    player.attachViews(layout, null, true, false)
                }
            },
        )

        if (showInfo && !showList) {
            InfoBar(
                number = index + 1,
                channel = current,
                nowNext = viewModel.nowNext(current),
                nowMs = viewModel.nowMs(),
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }

        if (showList) {
            ChannelListOverlay(
                channels = channels,
                selectedIndex = index,
                firstFocus = listFocus,
                nowTitleFor = { viewModel.nowNext(it).now?.title },
                onSelect = { index = it; showList = false },
            )
        }

        if (numberEntry.isNotEmpty() && !showList) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xE6000000))
                    .padding(horizontal = 28.dp, vertical = 16.dp),
            ) {
                Text(
                    numberEntry,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                )
            }
        }
    }
}

/**
 * Maps a hardware/remote key to its digit character, covering both the number
 * row and the numeric keypad. Returns null for non-digit keys.
 */
private fun digitFor(key: Key): Char? = when (key) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}

@Composable
private fun InfoBar(
    number: Int,
    channel: Channel,
    nowNext: com.dionysus.tv.data.iptv.NowNext,
    nowMs: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xE6000000))
            .padding(horizontal = 40.dp, vertical = 24.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(150.dp, 84.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF14141C)),
            contentAlignment = Alignment.Center,
        ) {
            if (channel.logo != null) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                )
            } else {
                Text(channel.name.take(3).uppercase(), color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 20.dp)) {
            Text(
                "$number  •  ${channel.name}",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFB0B8FF),
                maxLines = 1,
            )
            val now = nowNext.now
            Text(
                text = now?.title ?: "No information",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (now != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "${formatClock(now.startMs, java.util.TimeZone.getDefault())} – ${formatClock(now.stopMs, java.util.TimeZone.getDefault())}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xCCFFFFFF),
                    )
                    val frac = ((nowMs - now.startMs).toFloat() / (now.stopMs - now.startMs).coerceAtLeast(1))
                        .coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier.width(160.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x55FFFFFF)),
                    ) {
                        Box(Modifier.fillMaxWidth(frac).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
                    }
                }
                if (now.description.isNotBlank()) {
                    Text(
                        now.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xCCFFFFFF),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            nowNext.next?.let { next ->
                Text(
                    "Up next: ${next.title} · ${formatClock(next.startMs, java.util.TimeZone.getDefault())}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0x99FFFFFF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Text(
                "▲▼ change channel   OK: channel list   Back: exit",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0x77FFFFFF),
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun ChannelListOverlay(
    channels: List<Channel>,
    selectedIndex: Int,
    firstFocus: FocusRequester,
    nowTitleFor: (Channel) -> String?,
    onSelect: (Int) -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0))
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.42f)
            .background(Color(0xF00A0A12))
            .padding(vertical = 16.dp),
    ) {
        itemsIndexed(channels, key = { _, ch -> ch.id }) { i, channel ->
            ChannelListRow(
                number = i + 1,
                channel = channel,
                nowTitle = nowTitleFor(channel),
                selected = i == selectedIndex,
                modifier = if (i == selectedIndex) Modifier.focusRequester(firstFocus) else Modifier,
                onClick = { onSelect(i) },
            )
        }
    }
}

@Composable
private fun ChannelListRow(
    number: Int,
    channel: Channel,
    nowTitle: String?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val bg = when {
        focused -> MaterialTheme.colorScheme.primary
        selected -> Color(0x33FFFFFF)
        else -> Color.Transparent
    }
    val fg = if (focused) MaterialTheme.colorScheme.onPrimary else Color.White
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = 0.7f), modifier = Modifier.width(36.dp))
        Box(
            modifier = Modifier.size(48.dp, 40.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF14141C)),
            contentAlignment = Alignment.Center,
        ) {
            if (channel.logo != null) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(3.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(channel.name, style = MaterialTheme.typography.titleSmall, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (nowTitle != null) {
                Text(nowTitle, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
