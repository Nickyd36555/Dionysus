@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.dionysus.tv.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dionysus.tv.core.model.MediaItem
import com.dionysus.tv.core.model.MediaSource
import com.dionysus.tv.core.model.MediaType
import com.dionysus.tv.ui.components.MediaCard
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun SearchScreen(
    onOpenDetail: (String) -> Unit,
    onPlay: (url: String, title: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val aiResults by viewModel.aiResults.collectAsStateWithLifecycle()
    val aiLoading by viewModel.aiLoading.collectAsStateWithLifecycle()
    val aiMessage by viewModel.aiMessage.collectAsStateWithLifecycle()
    var filter by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(SearchFilter.ALL) }

    // VOD/Live items play directly; catalog items open their detail page.
    val open: (MediaItem) -> Unit = { item ->
        val url = item.streamUrl
        if (url != null) onPlay(url, item.title) else onOpenDetail(item.id)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                SearchField(value = query, onValueChange = viewModel::onQueryChange)
            }
            // Prominent, always-visible AI "Similar To" action next to the search box.
            AiSimilarButton(
                loading = aiLoading,
                enabled = query.trim().length >= 2,
                onClick = viewModel::aiSimilarSearch,
            )
        }

        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchFilter.entries.forEach { f ->
                FilterPill(label = f.label, selected = filter == f, onClick = { filter = f })
            }
        }

        val nothing = results.isEmpty() && aiResults.isEmpty() && !aiLoading &&
            aiMessage == null && query.trim().length >= 2
        if (nothing) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No results for \"$query\"",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val live = results.filter { it.source == MediaSource.LIVE_TV }
            val movies = results.filter { it.source != MediaSource.LIVE_TV && it.type == MediaType.MOVIE }
            val shows = results.filter { it.source != MediaSource.LIVE_TV && it.type == MediaType.TV_SHOW }
            val aiVisible = filter != SearchFilter.LIVE
            // Every id must be unique across the WHOLE grid — the sections share one grid, so
            // a title that appears both as an AI pick and in the catalog would collide on its
            // key and crash the grid. Dedup once (in render order) so each item lands in the
            // first section that holds it; computed in a remember so it's stable per data set.
            val sec = remember(results, aiResults, filter) {
                val seen = HashSet<String>()
                fun take(list: List<MediaItem>) = list.filter { seen.add(it.id) }
                val aiPicks = if (aiVisible) when (filter) {
                    SearchFilter.MOVIES -> aiResults.filter { it.type == MediaType.MOVIE }
                    SearchFilter.TV -> aiResults.filter { it.type == MediaType.TV_SHOW }
                    else -> aiResults
                } else emptyList()
                mapOf(
                    "ai" to take(aiPicks),
                    "movies" to if (filter == SearchFilter.ALL || filter == SearchFilter.MOVIES) take(movies) else emptyList(),
                    "shows" to if (filter == SearchFilter.ALL || filter == SearchFilter.TV) take(shows) else emptyList(),
                    "live" to if (filter == SearchFilter.ALL || filter == SearchFilter.LIVE) take(live) else emptyList(),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(124.dp),
                contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // AI "Similar To" picks lead the list when present.
                if (aiVisible && aiLoading) fullWidth("✨  Finding similar titles with AI…")
                if (aiVisible && !aiLoading && aiMessage != null) fullWidth("✨  ${aiMessage}")
                // section()/liveSection() early-return when empty, so calling unconditionally
                // is fine. Order stays Movies → TV Shows → Live TV.
                section("✨ Similar picks (AI)", sec.getValue("ai"), open)
                section("Movies", sec.getValue("movies"), open)
                section("TV Shows", sec.getValue("shows"), open)
                liveSection("On Live TV", sec.getValue("live"), open)
            }
        }
    }
}

/** Full-width status line inside the results grid (AI loading / error). */
private fun LazyGridScope.fullWidth(text: String) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    }
}

/** Prominent button that kicks off an AI "Similar To" search on the current query. */
@Composable
private fun AiSimilarButton(loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    val active = enabled && !loading
    // Filled and coloured so it's unmistakable; dimmed (not hidden) when inactive.
    val bg = when {
        !active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        focused -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.primary
    }
    val fg = if (focused && active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
    Text(
        text = if (loading) "✨ Finding…" else "✨ Similar (AI)",
        style = MaterialTheme.typography.titleMedium,
        color = fg,
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .then(if (focused && active) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = active, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 16.dp),
    )
}

private enum class SearchFilter(val label: String) {
    ALL("All"), MOVIES("Movies"), TV("TV Shows"), LIVE("Live TV")
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
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
        style = MaterialTheme.typography.titleSmall,
        color = fg,
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .then(if (selected && !focused) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

/** A full-width header followed by that type's cards, within one grid. */
private fun LazyGridScope.section(
    title: String,
    items: List<MediaItem>,
    onOpen: (MediaItem) -> Unit,
) {
    if (items.isEmpty()) return
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    }
    items(items, key = { it.id }) { item ->
        MediaCard(item = item, onClick = { onOpen(item) }, showSourceBadge = true, showTitle = true)
    }
}

/** Full-width descriptive rows for Live TV results (channel, air time, synopsis). */
private fun LazyGridScope.liveSection(
    title: String,
    items: List<MediaItem>,
    onOpen: (MediaItem) -> Unit,
) {
    if (items.isEmpty()) return
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    }
    items(items, key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { item ->
        LiveResultRow(item = item, onClick = { onOpen(item) })
    }
}

@Composable
private fun LiveResultRow(item: MediaItem, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    val fg = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp, 56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0B0B10)),
            contentAlignment = Alignment.Center,
        ) {
            if (item.posterUrl != null) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 1)
            item.subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
            if (item.overview.isNotBlank()) {
                Text(
                    item.overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text = "Search movies & shows…",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.titleMedium.fontSize,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
