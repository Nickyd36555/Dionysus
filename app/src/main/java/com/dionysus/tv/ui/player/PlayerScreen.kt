@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.dionysus.tv.ui.player

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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

/** Which secondary panel (if any) is showing over the video. */
private enum class Panel { NONE, AUDIO, SUBTITLE, SPEED, ASPECT, SYNC, INFO, EXTERNAL }

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
private val ASPECTS = listOf("Fit", "16:9", "4:3", "Zoom", "Original")

/**
 * Full-featured internal player backed by LibVLC (software decoders for HEVC,
 * AC3/EAC3/DTS/TrueHD, VP9, AV1, …). On-screen controls cover play/pause,
 * skip, audio & subtitle track selection, playback speed, aspect ratio/zoom,
 * and audio/subtitle sync — all D-pad navigable, and tap-friendly too.
 */
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val rootFocus = remember { FocusRequester() }
    val barFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }

    // libvlc-all bundles the full software codec set (HEVC/H.264/VP9/AV1 video;
    // AAC/AC3/E-AC3 "Dolby Digital+"/DTS/DTS-HD/TrueHD/FLAC/Opus audio), so
    // anything that won't hardware-decode still plays in software. HW decode is
    // enabled per-media (setHWDecoderEnabled) with an automatic software fallback.
    // Post-processing/deinterlace quality is scaled to what the device can handle.
    val tier = remember { com.dionysus.tv.player.PlaybackTuning.tier(context) }
    val libVlc = remember {
        LibVLC(context, com.dionysus.tv.player.PlaybackTuning.libVlcOptions(tier, live = false))
    }
    val player = remember { MediaPlayer(libVlc) }

    var positionMs by remember { mutableLongStateOf(0L) }
    var lengthMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var seeked by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(Panel.NONE) }

    // User-adjustable playback settings.
    var speed by remember { mutableStateOf(1.0f) }
    var aspect by remember { mutableStateOf("Fit") }
    var audioDelayMs by remember { mutableLongStateOf(0L) }
    var subDelayMs by remember { mutableLongStateOf(0L) }

    // Playback lifecycle: distinguishes "still buffering" from "actually failed"
    // so a stream that never loads shows an error instead of a black LIVE screen.
    var hasPlayed by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf(false) }
    var retryCount by remember { mutableIntStateOf(0) }
    val errorFocus = remember { FocusRequester() }

    // Auto-select English audio once tracks are available. Only fires once per load,
    // and never after the user has manually chosen a track (which sets this true).
    var audioAutoSelected by remember { mutableStateOf(false) }

    // Only a stream that IS playing yet reports no length is genuinely live.
    // A black screen that never started (hasPlayed == false) is a failure, not live.
    val isLive = hasPlayed && lengthMs <= 0

    fun bump() { controlsVisible = true; interaction++ }
    fun showControls() { controlsVisible = true; interaction++ }
    fun togglePlay() { if (player.isPlaying) player.pause() else player.play(); bump() }
    fun seekBy(deltaMs: Long) {
        val len = player.length
        val target = (player.time + deltaMs).coerceIn(0L, if (len > 0) len else Long.MAX_VALUE)
        player.time = target
        positionMs = target
        bump()
    }
    fun closePanel() { panel = Panel.NONE; bump() }

    val uri = remember { Uri.parse(viewModel.url) }
    // Holds the open file descriptor for content:// sources; kept across retries
    // and closed on dispose. A one-slot array so the lambdas can swap it.
    val pfdHolder = remember { arrayOfNulls<android.os.ParcelFileDescriptor>(1) }

    // Report hard failures so the UI can show an actionable error instead of a
    // black screen. LibVLC events arrive on its own thread; Compose snapshot
    // state is safe to write from there.
    DisposableEffect(Unit) {
        player.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.EncounteredError -> playbackError = true
                MediaPlayer.Event.Playing -> { hasPlayed = true; playbackError = false }
                else -> Unit
            }
        }
        onDispose {
            viewModel.saveProgress(player.time, player.length)
            player.setEventListener(null)
            player.stop()
            player.detachViews()
            player.release()
            libVlc.release()
            runCatching { pfdHolder[0]?.close() }
        }
    }

    // (Re)load the media. Re-runs on Retry by bumping retryCount.
    LaunchedEffect(retryCount) {
        hasPlayed = false
        playbackError = false
        seeked = false
        audioAutoSelected = false
        runCatching { if (retryCount > 0) player.stop() }
        runCatching { pfdHolder[0]?.close() }
        pfdHolder[0] = null
        // SAF-downloaded files are content:// — LibVLC needs a file descriptor for those.
        val pfd = if (uri.scheme == "content") {
            runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
        } else null
        pfdHolder[0] = pfd
        val media = if (pfd != null) {
            Media(libVlc, pfd.fileDescriptor)
        } else {
            Media(libVlc, uri)
        }.apply { setHWDecoderEnabled(true, false) }
        player.media = media
        media.release()
        // Audio passthrough: bitstream Dolby/DTS/TrueHD/Atmos to a receiver instead
        // of decoding to PCM, when the user has enabled it AND the device path can do
        // it. Best-effort — never let this stop playback.
        runCatching {
            player.setAudioDigitalOutputEnabled(viewModel.audioPassthrough.value && player.canDoPassthrough())
        }
        player.play()
    }

    // Apply playback settings whenever the user changes them.
    LaunchedEffect(speed) { runCatching { player.rate = speed } }
    LaunchedEffect(aspect) { runCatching { applyAspect(player, aspect) } }
    LaunchedEffect(audioDelayMs) { runCatching { player.audioDelay = audioDelayMs * 1000 } }
    LaunchedEffect(subDelayMs) { runCatching { player.spuDelay = subDelayMs * 1000 } }

    // Poll player for UI + apply the resume point once the length is known.
    LaunchedEffect(retryCount) {
        var stalled = 0L
        while (true) {
            positionMs = player.time
            lengthMs = player.length
            isPlaying = player.isPlaying
            if (player.isPlaying || lengthMs > 0) hasPlayed = true

            // Once tracks are known, prefer the English audio track if there is one
            // and it isn't already selected. Runs once per load; a manual choice in
            // the Audio panel disables it so the user's pick always wins.
            if (!audioAutoSelected && player.isPlaying) {
                val tracks = runCatching { player.audioTracks?.toList() }.getOrNull().orEmpty()
                if (tracks.isNotEmpty()) {
                    audioAutoSelected = true
                    val english = tracks.firstOrNull { t ->
                        val n = t.name?.lowercase().orEmpty()
                        "english" in n || Regex("\\beng?\\b").containsMatchIn(n)
                    }
                    if (english != null && english.id != player.audioTrack) {
                        runCatching { player.audioTrack = english.id }
                    }
                }
            }
            val resume = viewModel.startPositionMs.value
            if (!seeked && lengthMs > 0 && resume != null && resume > 3_000) {
                player.time = resume
                seeked = true
            }
            // Nothing ever started after a grace period → treat as a failed source
            // (bad URL, dead link, TLS chain error) rather than an endless black screen.
            if (!hasPlayed && !playbackError) {
                stalled += 500
                if (stalled >= 15_000) playbackError = true
            } else {
                stalled = 0
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

    // Auto-hide controls after inactivity — but never while a panel is open.
    LaunchedEffect(interaction, panel) {
        if (panel != Panel.NONE) return@LaunchedEffect
        controlsVisible = true
        delay(6_000)
        controlsVisible = false
    }

    // Keep focus where it belongs as the UI state changes.
    LaunchedEffect(controlsVisible, panel, playbackError) {
        if (playbackError) return@LaunchedEffect // error overlay owns focus
        runCatching {
            when {
                panel != Panel.NONE -> panelFocus.requestFocus()
                controlsVisible -> barFocus.requestFocus()
                else -> rootFocus.requestFocus()
            }
        }
    }

    BackHandler {
        when {
            panel != Panel.NONE -> closePanel()
            controlsVisible -> { controlsVisible = false; runCatching { rootFocus.requestFocus() } }
            else -> {
                viewModel.saveProgress(player.time, player.length)
                onBack()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures { controlsVisible = !controlsVisible; interaction++ }
            }
            // Seen before children: keeps the control bar alive and reachable. Any
            // press resets the auto-hide timer (so it can't vanish mid-navigation);
            // the first press while hidden reveals the bar and moves focus into it.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (event.key == Key.Back) return@onPreviewKeyEvent false // leave Back to BackHandler
                if (playbackError) return@onPreviewKeyEvent false          // error overlay owns input
                val wasHidden = !controlsVisible
                controlsVisible = true
                interaction++ // reset the 6s auto-hide countdown on every key
                if (wasHidden && panel == Panel.NONE) {
                    runCatching { barFocus.requestFocus() }
                    true // consume: first press only reveals + enters the bar
                } else {
                    false // pass through to the focused control
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                // Media-remote transport keys always work.
                when (event.key) {
                    Key.MediaPlayPause -> return@onKeyEvent run { togglePlay(); true }
                    Key.MediaRewind -> return@onKeyEvent run { seekBy(-10_000); true }
                    Key.MediaFastForward -> return@onKeyEvent run { seekBy(10_000); true }
                }
                // While the bar is visible, DO NOT consume the D-pad — the focus system
                // must be free to move focus into and across the control buttons.
                // Consuming Down here was exactly why the buttons were unreachable.
                if (controlsVisible) return@onKeyEvent false
                // Controls hidden: D-pad reveals them / quick-seeks.
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.Spacebar -> { showControls(); true }
                    Key.DirectionLeft -> { seekBy(-10_000); true }
                    Key.DirectionRight -> { seekBy(10_000); true }
                    Key.DirectionUp, Key.DirectionDown -> { showControls(); true }
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

        // Buffering: content is loading but hasn't started and hasn't failed.
        if (!hasPlayed && !playbackError) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Loading…",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }
        }

        // Hard failure: give the user something to do instead of a black screen.
        if (playbackError) {
            LaunchedEffect(Unit) { runCatching { errorFocus.requestFocus() } }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE6000000))
                    .padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Couldn't play this source",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                Text(
                    "Try another source, check the box's date & time (set it to Automatic), " +
                        "or turn on Settings → Connection → Allow insecure connections.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xCCFFFFFF),
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 24.dp),
                ) {
                    com.dionysus.tv.ui.components.AppButton(
                        onClick = { retryCount++ },
                        modifier = Modifier.focusRequester(errorFocus),
                    ) { Text("Retry") }
                    com.dionysus.tv.ui.components.AppButton(onClick = onBack) { Text("Back") }
                }
            }
        }

        // Secondary panels (audio/subtitle/speed/aspect/sync/info/external).
        if (panel != Panel.NONE) {
            OptionsPanel(
                panel = panel,
                player = player,
                speed = speed,
                aspect = aspect,
                audioDelayMs = audioDelayMs,
                subDelayMs = subDelayMs,
                firstFocus = panelFocus,
                externalPlayers = remember { viewModel.installedExternalPlayers() },
                onSelectAudio = { audioAutoSelected = true; player.audioTrack = it; closePanel() },
                onSelectSubtitle = { player.spuTrack = it; closePanel() },
                onSelectSpeed = { speed = it; closePanel() },
                onSelectAspect = { aspect = it; closePanel() },
                onAudioDelay = { audioDelayMs += it; bump() },
                onSubDelay = { subDelayMs += it; bump() },
                onLaunchExternal = { ext ->
                    val ok = viewModel.openExternally(ext, player.time)
                    android.widget.Toast.makeText(
                        context,
                        if (ok) "Opening in ${ext.displayName}…" else "Couldn't open ${ext.displayName}",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                    if (ok) onBack() else closePanel()
                },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        if (controlsVisible && !playbackError) {
            PlayerControls(
                title = viewModel.title,
                isPlaying = isPlaying,
                isLive = isLive,
                positionMs = positionMs,
                lengthMs = lengthMs,
                speed = speed,
                barFocus = barFocus,
                onTogglePlay = { togglePlay() },
                onRestart = { player.time = 0L; positionMs = 0L; bump() },
                onSeekBack = { seekBy(-10_000) },
                onSeekForward = { seekBy(10_000) },
                onSeekBack60 = { seekBy(-60_000) },
                onSeekForward60 = { seekBy(60_000) },
                onSeekTo = { player.time = it; positionMs = it; bump() },
                onOpenPanel = { panel = it; interaction++ },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Applies an aspect-ratio / zoom preset to the running player. */
private fun applyAspect(player: MediaPlayer, mode: String) {
    when (mode) {
        "16:9" -> { player.setAspectRatio("16:9"); player.setScale(0f) }
        "4:3" -> { player.setAspectRatio("4:3"); player.setScale(0f) }
        "Zoom" -> { player.setAspectRatio(null); player.setScale(1.3f) }
        "Original" -> { player.setAspectRatio(null); player.setScale(1.0f) }
        else -> { player.setAspectRatio(null); player.setScale(0f) } // Fit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerControls(
    title: String,
    isPlaying: Boolean,
    isLive: Boolean,
    positionMs: Long,
    lengthMs: Long,
    speed: Float,
    barFocus: FocusRequester,
    onTogglePlay: () -> Unit,
    onRestart: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBack60: () -> Unit,
    onSeekForward60: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onOpenPanel: (Panel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusGroup() // makes D-pad traversal into/across the controls reliable
            .background(Color(0xE6000000))
            .padding(horizontal = 40.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isLive) {
                Text(
                    "● LIVE",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFFF5252),
                )
            }
        }

        if (!isLive) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(formatTime(positionMs), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                SeekBar(
                    positionMs = positionMs,
                    lengthMs = lengthMs,
                    onSeekTo = onSeekTo,
                    modifier = Modifier.weight(1f),
                )
                Text(formatTime(lengthMs), style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ControlButton(
                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                label = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.focusRequester(barFocus),
                onClick = onTogglePlay,
            )
            if (!isLive) {
                ControlButton(Icons.Default.Replay, "Restart", onClick = onRestart)
                ControlButton(Icons.Default.FastRewind, "-60s", onClick = onSeekBack60)
                ControlButton(Icons.Default.Replay10, "-10s", onClick = onSeekBack)
                ControlButton(Icons.Default.Forward10, "+10s", onClick = onSeekForward)
                ControlButton(Icons.Default.FastForward, "+60s", onClick = onSeekForward60)
            }
            ControlButton(Icons.Default.Audiotrack, "Audio") { onOpenPanel(Panel.AUDIO) }
            ControlButton(Icons.Default.Subtitles, "Subtitles") { onOpenPanel(Panel.SUBTITLE) }
            ControlButton(Icons.Default.Speed, "${trimSpeed(speed)}x") { onOpenPanel(Panel.SPEED) }
            ControlButton(Icons.Default.AspectRatio, "Aspect") { onOpenPanel(Panel.ASPECT) }
            ControlButton(Icons.Default.Tune, "Sync") { onOpenPanel(Panel.SYNC) }
            ControlButton(Icons.Default.Info, "Info") { onOpenPanel(Panel.INFO) }
            ControlButton(Icons.Default.OpenInNew, "External") { onOpenPanel(Panel.EXTERNAL) }
        }
    }
}

/** A focusable seek bar: Left/Right scrubs ±10s while it has focus. */
@Composable
private fun SeekBar(
    positionMs: Long,
    lengthMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val fraction = if (lengthMs > 0) (positionMs.toFloat() / lengthMs).coerceIn(0f, 1f) else 0f
    Box(
        modifier = modifier
            .height(if (focused) 10.dp else 5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0x55FFFFFF))
            .focusable(interactionSource = interaction)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        onSeekTo((positionMs - 10_000).coerceAtLeast(0)); true
                    }
                    Key.DirectionRight -> {
                        onSeekTo((positionMs + 10_000).coerceAtMost(lengthMs)); true
                    }
                    else -> false
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    val bg = if (focused) MaterialTheme.colorScheme.primary else Color(0x33FFFFFF)
    val fg = if (focused) MaterialTheme.colorScheme.onPrimary else Color.White
    Column(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(26.dp))
        Text(label, color = fg, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun OptionsPanel(
    panel: Panel,
    player: MediaPlayer,
    speed: Float,
    aspect: String,
    audioDelayMs: Long,
    subDelayMs: Long,
    firstFocus: FocusRequester,
    externalPlayers: List<com.dionysus.tv.player.ExternalPlayer>,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSelectAspect: (String) -> Unit,
    onAudioDelay: (Long) -> Unit,
    onSubDelay: (Long) -> Unit,
    onLaunchExternal: (com.dionysus.tv.player.ExternalPlayer) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(0.42f)
            .background(Color(0xF2101014))
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (panel) {
            Panel.AUDIO -> {
                item { PanelTitle("Audio Track") }
                val tracks = runCatching { player.audioTracks?.toList() }.getOrNull().orEmpty()
                val current = player.audioTrack
                if (tracks.isEmpty()) item { PanelHint("No audio tracks reported.") }
                itemsIndexed(tracks) { index, track ->
                    PanelRow(
                        label = track.name + if (track.id == current) "   ✓" else "",
                        onClick = { onSelectAudio(track.id) },
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
            }
            Panel.SUBTITLE -> {
                item { PanelTitle("Subtitles") }
                val tracks = runCatching { player.spuTracks?.toList() }.getOrNull().orEmpty()
                val current = player.spuTrack
                itemsIndexed(tracks) { index, track ->
                    PanelRow(
                        label = track.name + if (track.id == current) "   ✓" else "",
                        onClick = { onSelectSubtitle(track.id) },
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
                if (tracks.isEmpty()) item { PanelHint("No subtitles in this stream.") }
            }
            Panel.SPEED -> {
                item { PanelTitle("Playback Speed") }
                itemsIndexed(SPEEDS) { index, value ->
                    PanelRow(
                        label = "${trimSpeed(value)}x" + if (value == speed) "   ✓" else "",
                        onClick = { onSelectSpeed(value) },
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
            }
            Panel.ASPECT -> {
                item { PanelTitle("Aspect Ratio") }
                itemsIndexed(ASPECTS) { index, value ->
                    PanelRow(
                        label = value + if (value == aspect) "   ✓" else "",
                        onClick = { onSelectAspect(value) },
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
            }
            Panel.SYNC -> {
                item { PanelTitle("Audio & Subtitle Sync") }
                item { PanelHint("Audio delay: ${audioDelayMs} ms") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PanelRow(label = "Audio −50ms", onClick = { onAudioDelay(-50) }, modifier = Modifier.weight(1f).focusRequester(firstFocus))
                        PanelRow(label = "Audio +50ms", onClick = { onAudioDelay(50) }, modifier = Modifier.weight(1f))
                    }
                }
                item { PanelHint("Subtitle delay: ${subDelayMs} ms") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PanelRow(label = "Subs −50ms", onClick = { onSubDelay(-50) }, modifier = Modifier.weight(1f))
                        PanelRow(label = "Subs +50ms", onClick = { onSubDelay(50) }, modifier = Modifier.weight(1f))
                    }
                }
            }
            Panel.INFO -> {
                item { PanelTitle("Stream Info") }
                val vt = runCatching { player.currentVideoTrack }.getOrNull()
                if (vt != null) {
                    item { PanelHint("Resolution: ${vt.width} × ${vt.height}") }
                }
                item { PanelHint("Length: ${formatTime(player.length)}") }
                val audioCount = runCatching { player.audioTracks?.size ?: 0 }.getOrDefault(0)
                item { PanelHint("Audio tracks: $audioCount") }
                val subCount = runCatching { player.spuTracks?.size ?: 0 }.getOrDefault(0)
                item { PanelHint("Subtitle tracks: $subCount") }
                item { PanelHint("Decoder: hardware with automatic software fallback (LibVLC).") }
                item { PanelHint("Codecs: HEVC/H.264/VP9/AV1 video · AC3/E-AC3/DTS/TrueHD/AAC/FLAC/Opus audio.") }
            }
            Panel.EXTERNAL -> {
                item { PanelTitle("Open In…") }
                if (externalPlayers.isEmpty()) {
                    item { PanelHint("No external players installed. Install Kodi, VLC, MX Player or nPlayer to hand streams off to them.") }
                }
                itemsIndexed(externalPlayers) { index, ext ->
                    PanelRow(
                        label = ext.displayName,
                        onClick = { onLaunchExternal(ext) },
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
            }
            Panel.NONE -> Unit
        }
    }
}

@Composable
private fun PanelTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = Color.White)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun PanelHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Color(0xBBFFFFFF))
}

@Composable
private fun PanelRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    com.dionysus.tv.ui.components.AppButton(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun trimSpeed(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString().trimEnd('0').trimEnd('.')

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
