@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.dionysus.tv.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dionysus.tv.data.iptv.Channel
import com.dionysus.tv.data.iptv.Programme
import com.dionysus.tv.ui.components.AppListItem
import com.dionysus.tv.ui.components.MediaCard
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LiveTvScreen(
    onPlay: (url: String, title: String) -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Live TV",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (state.hasPlaylists) {
                ModeToggle(
                    mode = state.mode,
                    hasVod = state.hasVod,
                    onSelect = viewModel::setMode,
                )
            }
        }

        when {
            !state.hasPlaylists && !state.isLoading -> EmptyState()
            state.isLoading && state.channels.isEmpty() && state.vod.isEmpty() ->
                CenterMessage("Loading channels…")
            state.mode == LiveTvMode.GUIDE -> EpgGuide(
                channels = state.guideChannels,
                upcomingFor = viewModel::upcoming,
                onPlay = onPlay,
            )
            state.mode == LiveTvMode.VOD -> Row(Modifier.fillMaxSize().padding(top = 12.dp)) {
                CategoryRail(
                    categories = state.vodCategories,
                    selected = state.selectedVodCategory,
                    onSelect = viewModel::selectVodCategory,
                    modifier = Modifier.width(240.dp).fillMaxHeight(),
                )
                Box(Modifier.fillMaxSize().padding(start = 16.dp)) {
                    VodGrid(vod = state.visibleVod, onPlay = onPlay)
                }
            }
            else -> Row(Modifier.fillMaxSize().padding(top = 12.dp)) {
                CategoryRail(
                    categories = state.categories,
                    selected = state.selectedCategory,
                    onSelect = viewModel::selectCategory,
                    modifier = Modifier.width(240.dp).fillMaxHeight(),
                )
                Box(Modifier.fillMaxSize().padding(start = 16.dp)) {
                    ChannelGrid(
                        channels = state.visibleChannels,
                        favorites = state.favorites,
                        error = state.error,
                        nowTitleFor = { viewModel.nowNext(it).now?.title },
                        onPlay = onPlay,
                        onToggleFavorite = viewModel::toggleFavorite,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeToggle(mode: LiveTvMode, hasVod: Boolean, onSelect: (LiveTvMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TogglePill("Channels", mode == LiveTvMode.CHANNELS) { onSelect(LiveTvMode.CHANNELS) }
        if (hasVod) {
            TogglePill("Movies (VOD)", mode == LiveTvMode.VOD) { onSelect(LiveTvMode.VOD) }
        }
        TogglePill("TV Guide", mode == LiveTvMode.GUIDE) { onSelect(LiveTvMode.GUIDE) }
    }
}

@Composable
private fun TogglePill(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(20.dp)
    val bg = when {
        focused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    val fg = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = fg,
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .then(if (selected && !focused) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .androidx_clickable(interaction, onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

private fun Modifier.androidx_clickable(interaction: MutableInteractionSource, onClick: () -> Unit): Modifier =
    this.clickable(interactionSource = interaction, indication = null, onClick = onClick)

@Composable
private fun CategoryRail(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(categories, key = { it }) { category ->
            AppListItem(
                selected = category == selected,
                onClick = { onSelect(category) },
                headlineContent = {
                    Text(category, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
        }
    }
}

@Composable
private fun ChannelGrid(
    channels: List<Channel>,
    favorites: Set<String>,
    error: String?,
    nowTitleFor: (Channel) -> String?,
    onPlay: (String, String) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
) {
    if (channels.isEmpty()) {
        CenterMessage(error ?: "No channels here yet.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(200.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        gridItems(channels, key = { it.id }) { channel ->
            ChannelCard(
                channel = channel,
                favorite = channel.id in favorites,
                nowTitle = nowTitleFor(channel),
                onClick = { onPlay(channel.streamUrl, channel.name) },
                onLongClick = { onToggleFavorite(channel) },
            )
        }
    }
}

@Composable
private fun VodGrid(vod: List<com.dionysus.tv.core.model.MediaItem>, onPlay: (String, String) -> Unit) {
    if (vod.isEmpty()) {
        CenterMessage("No VOD movies available.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        gridItems(vod, key = { it.id }) { item ->
            MediaCard(
                item = item,
                onClick = { item.streamUrl?.let { onPlay(it, item.title) } },
            )
        }
    }
}

@Composable
private fun EpgGuide(
    channels: List<Channel>,
    upcomingFor: (Channel) -> List<Programme>,
    onPlay: (String, String) -> Unit,
) {
    if (channels.isEmpty()) {
        CenterMessage("No EPG data. Add an EPG (XMLTV) URL to your playlist in Settings → Live TV.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(channels, key = { it.id }) { channel ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Channel label (also plays the channel).
                Box(
                    modifier = Modifier.width(150.dp).padding(end = 12.dp),
                ) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val programmes = upcomingFor(channel)
                    if (programmes.isEmpty()) {
                        item {
                            ProgrammeBlock(
                                time = "",
                                title = "No guide data",
                                isNow = false,
                                onClick = { onPlay(channel.streamUrl, channel.name) },
                            )
                        }
                    } else {
                        val now = System.currentTimeMillis()
                        items(programmes, key = { it.startMs }) { prog ->
                            ProgrammeBlock(
                                time = formatClock(prog.startMs),
                                title = prog.title,
                                isNow = now in prog.startMs until prog.stopMs,
                                onClick = { onPlay(channel.streamUrl, "${channel.name} — ${prog.title}") },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgrammeBlock(time: String, title: String, isNow: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    val bg = when {
        focused -> MaterialTheme.colorScheme.primary
        isNow -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color(0xFF1A1A22)
    }
    val fg = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .width(200.dp)
            .clip(shape)
            .background(bg)
            .then(if (isNow && !focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .androidx_clickable(interaction, onClick)
            .padding(12.dp),
    ) {
        Row {
            if (time.isNotBlank()) {
                Text(time, style = MaterialTheme.typography.labelMedium, color = fg)
                Text("  ", style = MaterialTheme.typography.labelMedium, color = fg)
            }
            if (isNow) {
                Text("● NOW", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFF5252))
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = fg,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChannelCard(
    channel: Channel,
    favorite: Boolean,
    nowTitle: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (focused) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0B0B10)),
            contentAlignment = Alignment.Center,
        ) {
            if (channel.logo != null) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            } else {
                Text(
                    channel.name.take(2).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (favorite) {
                Text(
                    "★",
                    color = Color(0xFFFFD54F),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
        }
        Text(
            text = channel.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = nowTitle ?: channel.group,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "No IPTV playlists yet",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Add an M3U URL or Xtream Codes account in Settings → Live TV (IPTV).",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                "Tip: hold OK on a channel to add it to Favorites.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun CenterMessage(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private fun formatClock(ms: Long): String = clockFormat.format(Date(ms))
