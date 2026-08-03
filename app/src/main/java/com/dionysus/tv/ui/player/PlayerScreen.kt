package com.dionysus.tv.ui.player

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Internal player backed by LibVLC, which ships software decoders for the codecs
 * device hardware often can't handle (HEVC, AC3/EAC3/DTS/TrueHD, VP9, AV1, …).
 * D-pad: OK toggles play/pause, left/right skip 10s; controls auto-hide.
 */
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    val libVlc = remember {
        LibVLC(
            context,
            arrayListOf(
                "--network-caching=3000",
                "--file-caching=3000",
                "--live-caching=3000",
                "--no-drop-late-frames",
                "--no-skip-frames",
                // Disable hardware direct-rendering, which causes green/blocky
                // glitches on many Android TV devices; slight cost, far smoother.
                "--no-mediacodec-dr",
                "--no-omxil-dr",
                "--audio-time-stretch",
            ),
        )
    }
    val player = remember { MediaPlayer(libVlc) }

    var positionMs by remember { mutableLongStateOf(0L) }
    var lengthMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableStateOf(0) }
    var seeked by remember { mutableStateOf(false) }
    var showTracks by remember { mutableStateOf(false) }
    val tracksFocus = remember { FocusRequester() }

    fun bump() { controlsVisible = true; interaction++ }
    fun togglePlay() { if (player.isPlaying) player.pause() else player.play() }
    fun seekBy(deltaMs: Long) {
        val len = player.length
        val target = (player.time + deltaMs).coerceIn(0L, if (len > 0) len else Long.MAX_VALUE)
        player.time = target
    }

    DisposableEffect(Unit) {
        val media = Media(libVlc, Uri.parse(viewModel.url)).apply { setHWDecoderEnabled(true, false) }
        player.media = media
        media.release()
        player.play()
        onDispose {
            viewModel.saveProgress(player.time, player.length)
            player.stop()
            player.detachViews()
            player.release()
            libVlc.release()
        }
    }

    // Poll player for UI + apply the resume point once the length is known.
    LaunchedEffect(Unit) {
        while (true) {
            positionMs = player.time
            lengthMs = player.length
            isPlaying = player.isPlaying
            val resume = viewModel.startPositionMs.value
            if (!seeked && lengthMs > 0 && resume != null && resume > 3_000) {
                player.time = resume
                seeked = true
            }
            delay(500)
        }
    }

    // Persist progress periodically.
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            if (player.isPlaying) viewModel.saveProgress(player.time, player.length)
        }
    }

    // Auto-hide controls a few seconds after the last interaction.
    LaunchedEffect(interaction) {
        controlsVisible = true
        delay(4_000)
        controlsVisible = false
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    BackHandler {
        if (showTracks) {
            showTracks = false
            focusRequester.requestFocus()
        } else {
            viewModel.saveProgress(player.time, player.length)
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures { controlsVisible = !controlsVisible; interaction++ }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.Spacebar, Key.MediaPlayPause -> {
                        togglePlay(); bump(); true
                    }
                    Key.DirectionLeft, Key.MediaRewind -> { seekBy(-10_000); bump(); true }
                    Key.DirectionRight, Key.MediaFastForward -> { seekBy(10_000); bump(); true }
                    Key.DirectionUp -> { showTracks = true; true }
                    Key.DirectionDown -> { bump(); true }
                    else -> false
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VLCVideoLayout(ctx).also { layout ->
                    // Keep focus on the Compose key handler, not the video surface.
                    layout.isFocusable = false
                    layout.isFocusableInTouchMode = false
                    layout.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    player.attachViews(layout, null, true, false)
                }
            },
        )

        if (controlsVisible && !showTracks) {
            PlayerControls(
                title = viewModel.title,
                isPlaying = isPlaying,
                positionMs = positionMs,
                lengthMs = lengthMs,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (showTracks) {
            LaunchedEffect(Unit) { runCatching { tracksFocus.requestFocus() } }
            TrackMenu(
                audioTracks = runCatching { player.audioTracks?.toList() }.getOrNull().orEmpty(),
                subtitleTracks = runCatching { player.spuTracks?.toList() }.getOrNull().orEmpty(),
                currentAudio = player.audioTrack,
                currentSubtitle = player.spuTrack,
                firstFocus = tracksFocus,
                onSelectAudio = { id -> player.audioTrack = id; showTracks = false; focusRequester.requestFocus() },
                onSelectSubtitle = { id -> player.spuTrack = id; showTracks = false; focusRequester.requestFocus() },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun TrackMenu(
    audioTracks: List<MediaPlayer.TrackDescription>,
    subtitleTracks: List<MediaPlayer.TrackDescription>,
    currentAudio: Int,
    currentSubtitle: Int,
    firstFocus: FocusRequester,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier
            .fillMaxWidth(0.42f)
            .background(Color(0xF0101014))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Audio", style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
        itemsIndexed(audioTracks) { index, track ->
            TrackButton(
                label = track.name + if (track.id == currentAudio) "  ✓" else "",
                onClick = { onSelectAudio(track.id) },
                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
        item {
            Text(
                "Subtitles",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        items(subtitleTracks) { track ->
            TrackButton(
                label = track.name + if (track.id == currentSubtitle) "  ✓" else "",
                onClick = { onSelectSubtitle(track.id) },
            )
        }
    }
}

@Composable
private fun TrackButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    com.dionysus.tv.ui.components.AppButton(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Text(label)
    }
}

@Composable
private fun PlayerControls(
    title: String,
    isPlaying: Boolean,
    positionMs: Long,
    lengthMs: Long,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .padding(horizontal = 48.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(24.dp)),
            )
            Text(formatTime(positionMs), style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x55FFFFFF)),
            ) {
                val fraction = if (lengthMs > 0) (positionMs.toFloat() / lengthMs).coerceIn(0f, 1f) else 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Text(formatTime(lengthMs), style = MaterialTheme.typography.bodyMedium, color = Color.White)
        }
        Text(
            text = "OK: play/pause   ◄ ►: skip 10s   ▲: audio/subtitles   Back: exit",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xAAFFFFFF),
        )
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
