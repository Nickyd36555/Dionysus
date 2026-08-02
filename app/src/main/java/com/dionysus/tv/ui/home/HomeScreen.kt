@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.dionysus.tv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dionysus.tv.ui.components.FeaturedCarousel
import com.dionysus.tv.ui.components.MediaRow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun HomeScreen(
    onOpenDetail: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.isLoading -> CenteredMessage("Loading…")
        state.error != null -> CenteredMessage(state.error!!)
        state.rows.isEmpty() && state.featured.isEmpty() ->
            CenteredMessage("Add a TMDB API key in Settings → Metadata to start browsing.")
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp),
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
            items(state.rows, key = { it.id }) { row ->
                MediaRow(
                    title = row.title,
                    items = row.items,
                    onItemClick = { onOpenDetail(it.id) },
                )
            }
        }
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
