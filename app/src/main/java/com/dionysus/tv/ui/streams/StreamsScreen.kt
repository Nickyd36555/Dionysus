@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.dionysus.tv.ui.streams

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dionysus.tv.core.model.StreamSource
import com.dionysus.tv.ui.components.AppButton
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
            else -> LazyColumn(
                contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.sources, key = { it.title + it.provider + (it.infoHash ?: "") }) { source ->
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

@Composable
private fun SourceRow(source: StreamSource, onPlay: () -> Unit, onDownload: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QualityBadge(source.quality.label)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(
                text = source.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(source.provider)
                    source.sizeBytes?.let { append("  •  ${formatSize(it)}") }
                    source.seeders?.let { append("  •  $it seeders") }
                    if (source.cachedOn.isNotEmpty()) {
                        append("  •  cached: ${source.cachedOn.joinToString { it.displayName }}")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
        // Two distinct, focusable actions so Play and Download are both reachable.
        AppButton(onClick = onPlay, modifier = Modifier.padding(end = 8.dp)) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text("  Play")
        }
        AppButton(onClick = onDownload) {
            Icon(Icons.Default.Download, contentDescription = null)
            Text("  Download")
        }
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
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.height(200.dp).clip(RoundedCornerShape(12.dp)),
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun QualityBadge(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
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
