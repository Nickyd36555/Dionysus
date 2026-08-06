@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.dionysus.tv.ui.detail

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dionysus.tv.core.model.Episode
import com.dionysus.tv.core.model.MediaItem
import com.dionysus.tv.core.model.MediaType
import com.dionysus.tv.ui.components.AppButton
import com.dionysus.tv.ui.components.AppListItem
import com.dionysus.tv.ui.components.MediaCard
import com.dionysus.tv.ui.theme.DionysusBackground
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun DetailScreen(
    onFindSources: (mediaId: String, season: Int?, episode: Int?) -> Unit,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit = {},
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val item = state.item

    if (state.isLoading || item == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = state.error ?: "Loading…",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(460.dp)) {
                AsyncImage(
                    model = item.backdropUrl ?: item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0.3f to DionysusBackground.copy(alpha = 0.2f),
                            1f to DionysusBackground,
                        ),
                    ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .width(820.dp)
                        .padding(start = 48.dp, bottom = 24.dp),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            item.year?.let { append(it) }
                            item.rating?.let { append("   ★ ${"%.1f".format(it)}") }
                            item.runtimeMinutes?.let { append("   ${it}m") }
                            if (item.genres.isNotEmpty()) append("   ${item.genres.take(3).joinToString(" • ")}")
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = item.overview,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(top = 20.dp),
                    ) {
                        if (item.type == MediaType.MOVIE) {
                            val resuming = state.resumePositionMs > 0
                            AppButton(onClick = { onFindSources(item.id, null, null) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Text(if (resuming) "  Resume ${formatResume(state.resumePositionMs)}" else "  Find Sources")
                            }
                        }
                        AppButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                imageVector = if (state.isFavorite) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = null,
                            )
                            Text(if (state.isFavorite) "  In My List" else "  My List")
                        }
                        AppButton(onClick = viewModel::findSimilar) {
                            Text(if (state.similarLoading) "✨ Finding…" else "✨ Similar")
                        }
                    }
                }
            }
        }

        state.extra?.let { extra ->
            item { ExtraInfoSection(extra) }
        }

        if (state.similarLoading || state.similar.isNotEmpty() || state.similarMessage != null) {
            item { SimilarSection(state, onOpenDetail) }
        }

        if (item.type == MediaType.TV_SHOW && state.seasons.isNotEmpty()) {
            item {
                Text(
                    text = "Seasons",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 48.dp, top = 24.dp, bottom = 12.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.seasons, key = { it.seasonNumber }) { season ->
                        AppButton(onClick = { viewModel.selectSeason(season.seasonNumber) }) {
                            Text(season.name.ifBlank { "Season ${season.seasonNumber}" })
                        }
                    }
                }
            }

            items(state.episodes, key = { it.episodeNumber }) { episode ->
                EpisodeItem(
                    episode = episode,
                    onClick = { onFindSources(item.id, episode.seasonNumber, episode.episodeNumber) },
                )
            }
        }
    }
}

/** AI "More like this" row on the detail page — Claude picks, resolved to cards. */
@Composable
private fun SimilarSection(state: DetailUiState, onOpenDetail: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text(
            text = "✨ More like this (AI)",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp),
        )
        when {
            state.similarLoading -> Text(
                "Finding similar titles with AI…",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 48.dp),
            )
            state.similarMessage != null -> Text(
                state.similarMessage,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 48.dp),
            )
            else -> LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.similar, key = { it.id }) { m: MediaItem ->
                    Box(Modifier.width(124.dp)) {
                        MediaCard(item = m, onClick = { onOpenDetail(m.id) }, showTitle = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtraInfoSection(extra: com.dionysus.tv.core.model.MovieExtra) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, end = 48.dp, top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Ratings chips (IMDb / Rotten Tomatoes / Metacritic).
        val ratings = buildList {
            extra.imdbRating?.let { add("IMDb  ⭐ $it") }
            extra.rottenTomatoes?.let { add("🍅 Rotten Tomatoes  $it") }
            extra.metacritic?.let { add("Ⓜ Metacritic  $it") }
            extra.rated?.let { add("Rated  $it") }
        }
        if (ratings.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ratings.forEach { chip ->
                    Text(
                        text = chip,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
        InfoLine("Cast", extra.cast)
        InfoLine("Director", extra.director)
        InfoLine("Writer", extra.writer)
        InfoLine("Awards", extra.awards)
        InfoLine("Box Office", extra.boxOffice)
    }
}

@Composable
private fun InfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row {
        Text(
            text = "$label:  ",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatResume(ms: Long): String {
    val totalMin = ms / 60000
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Composable
private fun EpisodeItem(episode: Episode, onClick: () -> Unit) {
    AppListItem(
        selected = false,
        onClick = onClick,
        headlineContent = {
            Text("${episode.episodeNumber}. ${episode.title}")
        },
        supportingContent = {
            if (episode.overview.isNotBlank()) {
                Text(
                    text = episode.overview,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp),
    )
}
