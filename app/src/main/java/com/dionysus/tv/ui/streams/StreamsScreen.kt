@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.dionysus.tv.ui.streams

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dionysus.tv.core.model.StreamSource
import com.dionysus.tv.ui.theme.DionysusGold
import com.dionysus.tv.ui.theme.DionysusGoldBright
import com.dionysus.tv.ui.theme.DionysusGoldDeep
import com.dionysus.tv.ui.theme.DionysusOnGold
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun StreamsScreen(
    onPlay: (url: String, title: String, progressId: String, poster: String?, backdrop: String?) -> Unit,
    onBack: () -> Unit,
    viewModel: StreamsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()

    LaunchedEffect(playback) {
        playback?.let {
            onPlay(it.url, it.title, it.progressId, it.posterUrl, it.backdropUrl)
            viewModel.consumePlayback()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(48.dp),
    ) {
        Text(
            text = "Sources — ${state.title}",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        state.message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        when {
            state.isLoading -> LoadingWithArt(state, "Searching sources for \"${state.title}\"…")
            state.resolvingTitle != null -> LoadingWithArt(state, "Resolving \"${state.resolvingTitle}\"…")
            state.error != null -> Centered(state.error!!)
            else -> {
                // Largest (best-quality) source first.
                val sorted = state.sources.sortedByDescending { it.sizeBytes ?: -1L }
                LazyColumn(
                    contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Index-prefixed key: two direct sources can share title+provider with no
                    // infoHash, and a duplicate key would crash the list. The index makes it unique.
                    itemsIndexed(
                        sorted,
                        key = { i, s -> "$i:${s.title}:${s.provider}:${s.infoHash ?: ""}" },
                    ) { _, source ->
                        SourceRow(
                            source = source,
                            onPlay = { viewModel.play(source) },
                            onDownload = { viewModel.download(source) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(source: StreamSource, onPlay: () -> Unit, onDownload: () -> Unit) {
    // Flat "stone tablet": sharp corners, a thin border, and a slim accent bar on
    // the left — cleaner and more classical than a rounded, bubbly card.
    val shape = RoundedCornerShape(3.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(56.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
        QualityBadge(source.quality.label)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = source.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    source.sizeBytes?.let { append(formatSize(it)) }
                    append(if (isEmpty()) source.provider else "  ·  ${source.provider}")
                    source.seeders?.let { append("  ·  $it seeders") }
                    if (source.cachedOn.isNotEmpty()) {
                        append("  ·  cached: ${source.cachedOn.joinToString { it.displayName }}")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = 0.3.sp,
            )
        }
        if (source.cachedOn.isNotEmpty()) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = "Cached",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        // Two distinct, focusable actions. Play is emphasized (filled); Download is
        // a lighter outline so the row reads calmer.
        ActionButton(label = "PLAY", icon = Icons.Default.PlayArrow, filled = true, onClick = onPlay)
        Spacer(Modifier.width(8.dp))
        ActionButton(label = "DOWNLOAD", icon = Icons.Default.Download, filled = false, onClick = onDownload)
        Spacer(Modifier.width(12.dp))
    }
}

/**
 * Embossed metallic action button, matching the app's 3D gold buttons.
 * PLAY (filled) is always a raised gold plate; DOWNLOAD (outlined) is a gold
 * frame at rest that fills into the same plate — and lifts — when focused.
 */
@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    filled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(3.dp)
    val plated = filled || focused
    val fill = when {
        pressed && plated -> Brush.verticalGradient(listOf(DionysusGoldDeep, DionysusGold)) // inset
        focused -> Brush.verticalGradient(listOf(DionysusGoldBright, DionysusGold))          // lifted
        filled -> Brush.verticalGradient(listOf(DionysusGold, DionysusGoldDeep))             // raised plate
        else -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))         // outlined
    }
    val bevel = Brush.verticalGradient(listOf(DionysusGoldBright, DionysusGoldDeep))
    val fg = if (plated) DionysusOnGold else DionysusGold
    Row(
        modifier = Modifier
            .shadow(
                elevation = if (!plated) 0.dp else if (focused) 10.dp else 4.dp,
                shape = shape,
                spotColor = DionysusGold,
                ambientColor = Color.Black,
            )
            .clip(shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .background(fill)
            .border(1.5.dp, bevel, shape) // beveled metallic frame
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.height(18.dp))
        Text(
            "  $label",
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            letterSpacing = 1.5.sp,
        )
    }
}

@Composable
private fun LoadingWithArt(state: StreamsUiState, message: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        state.backdropUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.25f),
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            state.posterUrl?.let { poster ->
                AsyncImage(
                    model = poster,
                    contentDescription = state.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.width(140.dp).height(210.dp).clip(RoundedCornerShape(12.dp)),
                )
            }
            // Show the title prominently so it's clear what's being scraped.
            Text(
                text = state.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QualityBadge(label: String) {
    Box(
        modifier = Modifier
            .padding(start = 14.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun Centered(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatSize(bytes: Long): String {
    val gb = bytes / 1_073_741_824.0
    if (gb >= 1) return "%.2f GB".format(gb)
    val mb = bytes / 1_048_576.0
    return "%.0f MB".format(mb)
}
