@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.dionysus.tv.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dionysus.tv.data.local.DownloadStatus
import com.dionysus.tv.data.local.entity.DownloadEntity
import com.dionysus.tv.ui.components.AppButton
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun DownloadsScreen(
    onPlay: (url: String, title: String) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(48.dp),
    ) {
        Text(
            text = "Downloads",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (downloads.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No downloads yet. Pick a source and tap Download to save it for offline, high-quality playback.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(downloads, key = { it.id }) { download ->
                    DownloadRow(
                        download = download,
                        onPlay = {
                            download.localPath?.let { onPlay("file://$it", download.title) }
                        },
                        onPause = { viewModel.pause(download) },
                        onResume = { viewModel.resume(download) },
                        onDelete = { viewModel.delete(download) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    download: DownloadEntity,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val status = runCatching { DownloadStatus.valueOf(download.status) }.getOrNull()
    val fraction = if (download.totalBytes > 0) {
        (download.bytesDownloaded.toFloat() / download.totalBytes).coerceIn(0f, 1f)
    } else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = download.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${download.quality}  •  ${statusLabel(status, fraction)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (status == DownloadStatus.DOWNLOADING || status == DownloadStatus.PAUSED) {
                ProgressBar(fraction, Modifier.padding(top = 8.dp))
            }
        }
        when (status) {
            DownloadStatus.COMPLETED -> {
                DownloadAction(Icons.Default.PlayArrow, "Play", onPlay)
                DownloadAction(Icons.Default.Delete, "Delete", onDelete)
            }
            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED, DownloadStatus.RESOLVING -> {
                DownloadAction(Icons.Default.Pause, "Pause", onPause)
                DownloadAction(Icons.Default.Close, "Cancel", onDelete)
            }
            DownloadStatus.PAUSED, DownloadStatus.FAILED -> {
                DownloadAction(Icons.Default.PlayArrow, "Resume", onResume)
                DownloadAction(Icons.Default.Close, "Cancel", onDelete)
            }
            else -> DownloadAction(Icons.Default.Delete, "Remove", onDelete)
        }
    }
}

@Composable
private fun DownloadAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    AppButton(onClick = onClick, modifier = Modifier.padding(start = 8.dp)) {
        Icon(icon, contentDescription = null)
        Text("  $label")
    }
}

@Composable
private fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

private fun statusLabel(status: DownloadStatus?, fraction: Float): String = when (status) {
    DownloadStatus.DOWNLOADING -> "Downloading ${(fraction * 100).toInt()}%"
    DownloadStatus.COMPLETED -> "Downloaded"
    DownloadStatus.FAILED -> "Failed"
    DownloadStatus.QUEUED -> "Queued"
    DownloadStatus.RESOLVING -> "Resolving…"
    DownloadStatus.PAUSED -> "Paused"
    null -> "Unknown"
}
