@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.dionysus.tv.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Top-level Movies (VOD) screen — a first-class menu-bar destination. Shows the
 * provider's on-demand movies grouped by category. Shares [LiveTvViewModel]
 * (which already loads VOD) but renders only the VOD browser.
 */
@Composable
fun MoviesScreen(
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
        Text(
            "Movies",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        when {
            state.isLoading && state.vod.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading movies…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            state.vod.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No movies (VOD) available",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Connect an Xtream Codes account in Settings → Live TV to see on-demand movies.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            else -> Row(Modifier.fillMaxSize().padding(top = 12.dp)) {
                SimpleCategoryRail(
                    categories = state.vodCategories,
                    selected = state.selectedVodCategory,
                    onSelect = viewModel::selectVodCategory,
                    modifier = Modifier.width(240.dp).fillMaxHeight(),
                )
                Box(Modifier.fillMaxSize().padding(start = 16.dp)) {
                    VodGrid(vod = state.visibleVod, onPlay = onPlay)
                }
            }
        }
    }
}
