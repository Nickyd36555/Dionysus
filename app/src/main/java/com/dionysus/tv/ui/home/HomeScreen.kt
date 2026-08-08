@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.dionysus.tv.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dionysus.tv.core.model.MediaItem
import com.dionysus.tv.data.settings.HomeTile
import com.dionysus.tv.ui.components.AppButton
import com.dionysus.tv.ui.components.FeaturedCarousel
import com.dionysus.tv.ui.components.MediaRow
import com.dionysus.tv.ui.theme.DionysusGold
import com.dionysus.tv.ui.theme.DionysusGoldBright
import com.dionysus.tv.ui.theme.DionysusGoldDeep
import com.dionysus.tv.ui.theme.DionysusOnGold
import com.dionysus.tv.ui.update.UpdateBanner
import com.dionysus.tv.ui.update.UpdateViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun HomeScreen(
    onOpenDetail: (String) -> Unit,
    onOpenTile: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    var menuItem by remember { mutableStateOf<MediaItem?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (updateState.isAvailable) {
                UpdateBanner(
                    state = updateState,
                    onUpdate = updateViewModel::update,
                    onDismiss = updateViewModel::dismiss,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    state.isLoading -> CenteredMessage("Loading…")
                    state.error != null -> CenteredMessage(state.error!!)
                    state.rows.isEmpty() && state.featured.isEmpty() ->
                        CenteredMessage("Add a TMDB API key in Settings → Metadata to start browsing.")
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        if (state.featured.isNotEmpty()) {
                            item {
                                FeaturedCarousel(
                                    items = state.featured,
                                    onPlay = { onOpenDetail(it.id) },
                                    onDetails = { onOpenDetail(it.id) },
                                )
                            }
                        }
                        if (state.tiles.isNotEmpty()) {
                            item { QuickTilesRow(tiles = state.tiles, onOpenTile = onOpenTile) }
                        }
                        items(state.rows, key = { it.id }) { row ->
                            MediaRow(
                                title = row.title,
                                items = row.items,
                                onItemClick = { onOpenDetail(it.id) },
                                onItemLongClick = if (row.isContinueWatching) ({ menuItem = it }) else null,
                            )
                        }
                    }
                }
            }
        }

        menuItem?.let { item ->
            ContinueWatchingMenu(
                item = item,
                onContinue = { onOpenDetail(item.id); menuItem = null },
                onFromBeginning = {
                    viewModel.removeFromContinueWatching(item.id)
                    onOpenDetail(item.id)
                    menuItem = null
                },
                onRemove = {
                    viewModel.removeFromContinueWatching(item.id)
                    menuItem = null
                },
                onDismiss = { menuItem = null },
            )
        }
    }
}

/** Long-press menu for a Continue Watching item. */
@Composable
private fun ContinueWatchingMenu(
    item: MediaItem,
    onContinue: () -> Unit,
    onFromBeginning: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            AppButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue watching") }
            AppButton(onClick = onFromBeginning, modifier = Modifier.fillMaxWidth()) { Text("Watch from beginning") }
            AppButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) { Text("Remove from Continue Watching") }
            AppButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}

/** The Disney/Marvel-style strip of customizable shortcut tiles. */
@Composable
private fun QuickTilesRow(tiles: List<HomeTile>, onOpenTile: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(tiles, key = { it.id }) { tile ->
            HomeTileChip(label = tile.label, onClick = { onOpenTile(tile.query) })
        }
    }
}

@Composable
private fun HomeTileChip(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(6.dp)
    // Gold frame at rest; fills into a raised gold plate on focus (matches the buttons).
    val fill = if (focused) {
        Brush.verticalGradient(listOf(DionysusGoldBright, DionysusGold))
    } else {
        Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface))
    }
    val border = if (focused) DionysusGoldBright else DionysusGoldDeep
    val fg = if (focused) DionysusOnGold else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .width(180.dp)
            .height(84.dp)
            .clip(shape)
            .background(fill)
            .border(if (focused) 2.dp else 1.dp, border, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
private fun CenteredMessage(message: String) {
    Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
