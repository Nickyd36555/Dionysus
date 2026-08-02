@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.dionysus.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dionysus.tv.BuildConfig
import com.dionysus.tv.player.ExternalPlayer
import com.dionysus.tv.ui.components.AppButton
import com.dionysus.tv.ui.components.AppListItem
import com.dionysus.tv.ui.update.UpdateViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            state.statusMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // ---- Debrid --------------------------------------------------------
        item { SectionHeader("Debrid Services") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Real-Debrid: " + when {
                        state.deviceCode != null -> "waiting for authorization…"
                        state.realDebridConnected -> "connected${state.realDebridUser?.let { " ($it)" } ?: ""}"
                        else -> "not connected"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.realDebridConnected) {
                        AppButton(onClick = viewModel::disconnectRealDebrid) { Text("Disconnect") }
                    } else {
                        AppButton(onClick = viewModel::connectRealDebrid) { Text("Connect Real-Debrid") }
                    }
                }
            }
        }
        item {
            SettingTextField(
                label = "Premiumize API key" + if (state.premiumizeConnected) "  ✓ connected" else "",
                value = state.premiumizeKey,
                onValueChange = viewModel::setPremiumizeKey,
                isSecret = true,
            )
        }
        item {
            ToggleRow(
                label = "Cached only (hide sources that aren't instantly playable)",
                enabled = state.onlyCached,
                onToggle = viewModel::setOnlyCached,
            )
        }

        // ---- Metadata ------------------------------------------------------
        item { SectionHeader("Metadata (TMDB)") }
        item {
            SettingTextField(
                label = "TMDB API key (required to browse the catalog)",
                value = state.tmdbKey,
                onValueChange = viewModel::setTmdbKey,
                isSecret = true,
            )
        }

        // ---- Scrapers ------------------------------------------------------
        item { SectionHeader("Scrapers") }
        item {
            SettingTextField(
                label = "Torrentio base URL",
                value = state.torrentioUrl,
                onValueChange = viewModel::setTorrentioUrl,
            )
        }
        item {
            SettingTextField(
                label = "Orion API key",
                value = state.orionKey,
                onValueChange = viewModel::setOrionKey,
                isSecret = true,
            )
        }
        items(state.scrapers, key = { it.id }) { scraper ->
            ToggleRow(
                label = scraper.name,
                enabled = scraper.enabled,
                onToggle = { viewModel.toggleScraper(scraper.id, it) },
            )
        }

        // ---- Players -------------------------------------------------------
        item { SectionHeader("Preferred Player") }
        items(state.availablePlayers, key = { it.id }) { player ->
            AppListItem(
                selected = player.id == state.preferredPlayer,
                onClick = { viewModel.setPreferredPlayer(player.id) },
                headlineContent = { Text(player.displayName) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(
                "Install VLC, nPlayer, or MX Player to see them here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- Home layout ---------------------------------------------------
        item { SectionHeader("Customize Home") }
        items(state.homeRows, key = { it.id }) { row ->
            ToggleRow(
                label = row.title,
                enabled = row.enabled,
                onToggle = { viewModel.toggleHomeRow(row.id, it) },
            )
        }

        // ---- App / updates -------------------------------------------------
        item { SectionHeader("App") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}" +
                        (updateState.info?.let { "  •  update to v${it.versionName} available" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                updateState.message?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppButton(onClick = updateViewModel::check, enabled = !updateState.checking) {
                        Text(if (updateState.checking) "Checking…" else "Check for Updates")
                    }
                    if (updateState.isAvailable) {
                        AppButton(onClick = updateViewModel::update, enabled = !updateState.downloading) {
                            Text(if (updateState.downloading) "Updating… ${(updateState.progress * 100).toInt()}%" else "Update now")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ToggleRow(label: String, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        AppButton(onClick = { onToggle(!enabled) }, modifier = Modifier.width(120.dp)) {
            Text(if (enabled) "On" else "Off")
        }
    }
}

@Composable
private fun SettingTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isSecret: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth(0.7f)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (isSecret) KeyboardType.Password else KeyboardType.Uri),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}
