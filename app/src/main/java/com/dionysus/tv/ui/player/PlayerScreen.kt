@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.dionysus.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(viewModel.url))
            prepare()
            playWhenReady = true
        }
    }

    // Persist the resume point every 10s while playing.
    LaunchedEffect(player) {
        while (true) {
            delay(10_000)
            if (player.isPlaying) {
                viewModel.saveProgress(player.currentPosition, player.duration.coerceAtLeast(0))
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveProgress(player.currentPosition, player.duration.coerceAtLeast(0))
            player.release()
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                setShowNextButton(false)
                setShowPreviousButton(false)
            }
        },
    )
}
