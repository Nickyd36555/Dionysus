@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.dionysus.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dionysus.tv.core.model.MediaItem
import com.dionysus.tv.ui.theme.DionysusBackground
import androidx.tv.material3.Button
import androidx.tv.material3.Carousel
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private val HeroHeight = 440.dp

/**
 * The home-screen hero: an auto-advancing, focusable carousel of backdrop
 * images with Play / More Info actions — the "fantastic" first impression.
 */
@Composable
fun FeaturedCarousel(
    items: List<MediaItem>,
    onPlay: (MediaItem) -> Unit,
    onDetails: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    Carousel(
        itemCount = items.size,
        modifier = modifier
            .fillMaxWidth()
            .height(HeroHeight),
    ) { index ->
        val item = items[index]
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.backdropUrl ?: item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Left-to-right scrim so text stays legible over any artwork.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to DionysusBackground,
                            0.5f to DionysusBackground.copy(alpha = 0.5f),
                            1f to DionysusBackground.copy(alpha = 0.0f),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.6f to DionysusBackground.copy(alpha = 0f),
                            1f to DionysusBackground,
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(720.dp)
                    .padding(start = 48.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.year?.let {
                    Text(
                        text = buildString {
                            append(it)
                            item.rating?.let { r -> append("   ★ ${"%.1f".format(r)}") }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Text(
                    text = item.overview,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 24.dp),
                ) {
                    Button(onClick = { onPlay(item) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text("  Play")
                    }
                    Button(onClick = { onDetails(item) }) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Text("  More Info")
                    }
                }
            }
        }
    }
}
