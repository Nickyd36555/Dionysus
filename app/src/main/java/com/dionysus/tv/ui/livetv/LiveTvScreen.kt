@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.dionysus.tv.ui.livetv

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
            if (state.epgLoading) {
                EpgUpdatingPill()
            }
        }

        when {
            !state.hasPlaylists && !state.isLoading -> EmptyState()
            state.isLoading && state.channels.isEmpty() ->
                CenterMessage("Loading channels…")
            else -> LiveView(
                state = state,
                viewModel = viewModel,
                onPlay = onPlay,
            )
        }
    }
}

/** Small floating "updating" indicator so the guide never looks frozen. */
@Composable
private fun EpgUpdatingPill() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "epg")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "angle",
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "⟳",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.graphicsLayer { rotationZ = angle },
        )
        Text("Updating guide…", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Combined Live TV view: category rail + now/next preview + timeline guide. */
@Composable
private fun LiveView(
    state: LiveTvUiState,
    viewModel: LiveTvViewModel,
    onPlay: (String, String) -> Unit,
) {
    val channels = state.visibleChannels
    var previewChannel by androidx.compose.runtime.remember(state.selectedCategory) {
        androidx.compose.runtime.mutableStateOf<Channel?>(null)
    }
    val preview = previewChannel ?: channels.firstOrNull()

    Row(Modifier.fillMaxSize().padding(top = 12.dp)) {
        CategoryRail(
            state = state,
            onRow = viewModel::onRowSelected,
            onSub = viewModel::selectSub,
            onBack = viewModel::backToGroups,
            onHideGroup = viewModel::hideGroup,
            onToggleHidden = viewModel::toggleShowHidden,
            modifier = Modifier.width(260.dp).fillMaxHeight(),
        )
        Column(Modifier.fillMaxSize().padding(start = 16.dp)) {
            if (preview != null) {
                NowNextPreview(channel = preview, nowNext = viewModel.nowNext(preview))
            }
            EpgGuide(
                channels = channels,
                favorites = state.favorites,
                programmesFor = viewModel::programmes,
                onPlay = onPlay,
                onFocusChannel = { previewChannel = it; viewModel.prefetchGuide(listOf(it)) },
                onToggleFavorite = viewModel::toggleFavorite,
            )
        }
    }
}

@Composable
private fun NowNextPreview(channel: Channel, nowNext: com.dionysus.tv.data.iptv.NowNext) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF14141C))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(120.dp, 68.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0B0B10)),
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
                Text(channel.name.take(2).uppercase(), color = MaterialTheme.colorScheme.primary)
            }
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(channel.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            val now = nowNext.now
            if (now != null) {
                Text(
                    "Now: ${now.title}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatClock(now.startMs)}–${formatClock(now.stopMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (now.description.isNotBlank()) {
                    Text(
                        now.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text("Press to watch live", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            nowNext.next?.let { next ->
                Text(
                    "Up next: ${next.title} · ${formatClock(next.startMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
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

/** Plain string-category rail (used by VOD). */
@Composable
internal fun SimpleCategoryRail(
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

/**
 * Grouped, drill-down channel category rail. Top level shows Favorites, All, and
 * parent groups (US, UK, Sports, 24/7, …) each with a count; groups with several
 * sub-categories drill in. Hold OK on a group to hide it; a footer toggle reveals
 * hidden groups so they can be restored.
 */
@Composable
private fun CategoryRail(
    state: LiveTvUiState,
    onRow: (CategoryRow) -> Unit,
    onSub: (CategoryRow) -> Unit,
    onBack: () -> Unit,
    onHideGroup: (String) -> Unit,
    onToggleHidden: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val drilled = state.selectedGroup != null
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (drilled) {
            item(key = "back") {
                CategoryRowItem(
                    label = "‹  ${state.selectedGroup}",
                    count = null,
                    selected = false,
                    isGroup = false,
                    hidden = false,
                    onClick = onBack,
                    onLongClick = null,
                )
            }
            items(state.subRows, key = { "sub:" + it.value }) { row ->
                CategoryRowItem(
                    label = row.label,
                    count = row.count,
                    selected = row.value == state.selectedCategory,
                    isGroup = false,
                    hidden = false,
                    onClick = { onSub(row) },
                    onLongClick = null,
                )
            }
        } else {
            items(state.topRows, key = { it.value }) { row ->
                CategoryRowItem(
                    label = row.label,
                    count = row.count,
                    selected = row.value == state.selectedCategory && !row.isGroup,
                    isGroup = row.isGroup,
                    hidden = row.hidden,
                    onClick = { onRow(row) },
                    onLongClick = if (row.isGroup) ({ onHideGroup(row.value) }) else null,
                )
            }
            if (state.hiddenGroups.isNotEmpty()) {
                item(key = "toggle-hidden") {
                    CategoryRowItem(
                        label = if (state.showHidden) "Hide hidden (${state.hiddenGroups.size})"
                        else "Show hidden (${state.hiddenGroups.size})",
                        count = null,
                        selected = false,
                        isGroup = false,
                        hidden = false,
                        onClick = onToggleHidden,
                        onLongClick = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRowItem(
    label: String,
    count: Int?,
    selected: Boolean,
    isGroup: Boolean,
    hidden: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    val bg = when {
        focused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    val fg = when {
        focused -> MaterialTheme.colorScheme.onPrimary
        hidden -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label + if (hidden) "  (hidden)" else "",
            style = MaterialTheme.typography.titleMedium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count != null) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = fg.copy(alpha = 0.8f),
            )
        }
        if (isGroup) {
            Text("  ›", style = MaterialTheme.typography.titleMedium, color = fg)
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
internal fun VodGrid(vod: List<com.dionysus.tv.core.model.MediaItem>, onPlay: (String, String) -> Unit) {
    if (vod.isEmpty()) {
        CenterMessage("No VOD movies available.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(124.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        gridItems(vod, key = { it.id }) { item ->
            MediaCard(
                item = item,
                onClick = { item.streamUrl?.let { onPlay(it, item.title) } },
                showTitle = true,
            )
        }
    }
}

// Timeline geometry.
private const val PX_PER_MIN = 5           // dp of width per minute of programme time
private const val SLOT_MIN = 30            // ruler tick every 30 minutes
private val CHANNEL_COL_WIDTH = 220.dp
private val GUIDE_ROW_HEIGHT = 76.dp

@Composable
private fun EpgGuide(
    channels: List<Channel>,
    favorites: Set<String>,
    programmesFor: (Channel) -> List<Programme>,
    onPlay: (String, String) -> Unit,
    onFocusChannel: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
) {
    if (channels.isEmpty()) {
        CenterMessage("No channels in this category.")
        return
    }
    val now = System.currentTimeMillis()
    val slotMs = SLOT_MIN * 60_000L
    // Start the timeline at the current half-hour so the on-now show is leftmost
    // (fully-past programmes are dropped), matching how TiViMate lays it out.
    val timelineStart = (now / slotMs) * slotMs
    val maxStop = channels.maxOf { ch -> programmesFor(ch).maxOfOrNull { it.stopMs } ?: (now + 3 * 3_600_000L) }
    val totalSlots = (((maxStop - timelineStart) / slotMs).toInt() + 1).coerceIn(6, 48)
    val scroll = rememberScrollState()

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
        // Time ruler (shares the horizontal scroll with every channel lane).
        Row {
            Box(Modifier.width(CHANNEL_COL_WIDTH)) {
                Text(
                    formatDay(now),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            Row(Modifier.horizontalScroll(scroll)) {
                repeat(totalSlots) { i ->
                    Text(
                        text = formatClock(timelineStart + i * slotMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width((SLOT_MIN * PX_PER_MIN).dp).padding(vertical = 8.dp),
                    )
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(channels, key = { it.id }) { channel ->
                Row(
                    modifier = Modifier.height(GUIDE_ROW_HEIGHT),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChannelLabel(
                        number = channels.indexOf(channel) + 1,
                        channel = channel,
                        favorite = channel.id in favorites,
                        onClick = { onPlay(channel.streamUrl, channel.name) },
                        onLongClick = { onToggleFavorite(channel) },
                        onFocused = { onFocusChannel(channel) },
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(scroll).fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val programmes = programmesFor(channel)
                            .filter { it.stopMs > timelineStart }
                            .sortedBy { it.startMs }
                        var cursor = timelineStart
                        programmes.forEach { prog ->
                            val start = maxOf(prog.startMs, timelineStart)
                            val gapMin = ((start - cursor) / 60_000L).toInt()
                            if (gapMin > 0) Spacer(Modifier.width((gapMin * PX_PER_MIN).dp))
                            val widthMin = ((prog.stopMs - start) / 60_000L).toInt().coerceAtLeast(6)
                            ProgrammeBlock(
                                widthDp = (widthMin * PX_PER_MIN).dp,
                                time = "${formatClock(prog.startMs)}–${formatClock(prog.stopMs)}",
                                title = prog.title,
                                isNow = now in prog.startMs until prog.stopMs,
                                onClick = { onPlay(channel.streamUrl, "${channel.name} — ${prog.title}") },
                            )
                            cursor = prog.stopMs
                        }
                        if (programmes.isEmpty()) {
                            ProgrammeBlock(
                                widthDp = 300.dp,
                                time = "",
                                title = "No guide data — press to watch",
                                isNow = false,
                                onClick = { onPlay(channel.streamUrl, channel.name) },
                            )
                        }
                    }
                }
            }
        }
        }

        // Vertical "now" line across the guide, tracking the shared scroll.
        val density = androidx.compose.ui.platform.LocalDensity.current
        val nowLineX = CHANNEL_COL_WIDTH +
            ((now - timelineStart) / 60_000f * PX_PER_MIN).dp -
            with(density) { scroll.value.toDp() }
        if (nowLineX >= CHANNEL_COL_WIDTH) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = nowLineX, top = 44.dp)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color(0xFFFF5252)),
            )
        }
    }
}

@Composable
private fun ChannelLabel(
    number: Int,
    channel: Channel,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    if (focused) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onFocused() }
    }
    Row(
        modifier = Modifier
            .width(CHANNEL_COL_WIDTH)
            .fillMaxHeight()
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) MaterialTheme.colorScheme.primary else Color(0xFF14141C))
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val fg = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = 0.7f))
        Box(
            modifier = Modifier.size(44.dp, 40.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF0B0B10)),
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
        Text(
            channel.name,
            style = MaterialTheme.typography.titleSmall,
            color = fg,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (favorite) Text("★", color = Color(0xFFFFD54F), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ProgrammeBlock(widthDp: androidx.compose.ui.unit.Dp, time: String, title: String, isNow: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(6.dp)
    val bg = when {
        focused -> MaterialTheme.colorScheme.primary
        isNow -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color(0xFF1A1A22)
    }
    val fg = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .width(widthDp)
            .fillMaxHeight()
            .padding(end = 4.dp)
            .clip(shape)
            .background(bg)
            .then(if (isNow && !focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row {
            if (isNow) Text("● ", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF5252))
            if (time.isNotBlank()) {
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = fg.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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

private val clockFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
private val dayFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
private fun formatClock(ms: Long): String = clockFormat.format(Date(ms))
private fun formatDay(ms: Long): String = dayFormat.format(Date(ms))
