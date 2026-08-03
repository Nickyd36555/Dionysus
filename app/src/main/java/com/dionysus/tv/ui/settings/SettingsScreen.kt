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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.Dialog
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

        // ---- Sign in / transfer -------------------------------------------
        item { SectionHeader("Sign in / Transfer (Sync code)") }
        item {
            Column(
                modifier = Modifier.fillMaxWidth(0.85f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Carry all your connections to another device: generate a code here, then paste it under Restore on the other device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppButton(onClick = viewModel::generateSyncCode) { Text("Generate sync code") }
                state.syncCode?.let { code ->
                    SelectionContainer {
                        Text(
                            text = code,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp),
                        )
                    }
                }
                SettingTextField(
                    label = "Restore: paste a sync code from another device",
                    value = state.importCodeInput,
                    onValueChange = viewModel::setImportCode,
                )
                AppButton(onClick = viewModel::restoreFromCode) { Text("Restore setup") }
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
        item {
            SettingTextField(
                label = "OMDb API key (extra info: cast, RT/Metacritic, awards)",
                value = state.omdbKey,
                onValueChange = viewModel::setOmdbKey,
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

        // ---- Add-ons -------------------------------------------------------
        item { SectionHeader("Add-ons (Stremio)") }
        item {
            Column(
                modifier = Modifier.fillMaxWidth(0.7f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingTextField(
                    label = "Add-on manifest URL (paste and install)",
                    value = state.addonUrlInput,
                    onValueChange = viewModel::setAddonUrl,
                )
                AppButton(onClick = viewModel::installAddon) { Text("Install add-on") }
            }
        }
        items(state.addons, key = { it.transportUrl }) { addon ->
            Row(
                modifier = Modifier.fillMaxWidth(0.9f),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        text = addon.manifest.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = addon.resourceNames.joinToString(", ").ifBlank { "add-on" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AppButton(
                    onClick = { viewModel.editAddon(addon.transportUrl) },
                    modifier = Modifier.padding(end = 8.dp),
                ) { Text("Edit") }
                AppButton(onClick = { viewModel.removeAddon(addon.transportUrl) }) { Text("Remove") }
            }
        }

        // ---- Live TV (IPTV) ------------------------------------------------
        item { SectionHeader("Live TV (IPTV)") }
        item {
            Column(
                modifier = Modifier.fillMaxWidth(0.85f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Add an M3U/M3U8 playlist URL, or connect an Xtream Codes account. Channels appear under Live TV in the sidebar. Add an EPG (XMLTV) URL to see what's on now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "M3U playlist",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
                SettingTextField(label = "Name (optional)", value = state.m3uName, onValueChange = viewModel::setM3uName)
                SettingTextField(label = "M3U / M3U8 URL", value = state.m3uUrl, onValueChange = viewModel::setM3uUrl)
                SettingTextField(label = "EPG (XMLTV) URL — optional", value = state.m3uEpgUrl, onValueChange = viewModel::setM3uEpgUrl)
                AppButton(onClick = viewModel::addM3uPlaylist) { Text("Add M3U playlist") }

                Text(
                    "Xtream Codes account",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp),
                )
                SettingTextField(label = "Name (optional)", value = state.xtreamName, onValueChange = viewModel::setXtreamName)
                SettingTextField(label = "Server URL (http://host:port)", value = state.xtreamHost, onValueChange = viewModel::setXtreamHost)
                SettingTextField(label = "Username", value = state.xtreamUser, onValueChange = viewModel::setXtreamUser)
                SettingTextField(label = "Password", value = state.xtreamPass, onValueChange = viewModel::setXtreamPass, isSecret = true)
                AppButton(onClick = viewModel::addXtreamPlaylist) { Text("Add Xtream account") }
            }
        }
        items(state.playlists, key = { it.id }) { playlist ->
            Row(
                modifier = Modifier.fillMaxWidth(0.9f),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (playlist.kind == "xtream") "Xtream • ${playlist.host}" else "M3U • ${playlist.url}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                AppButton(onClick = { viewModel.removePlaylist(playlist.id) }) { Text("Remove") }
            }
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

/**
 * A focusable settings row you can D-pad straight past. Tapping/OK opens a
 * popup to edit — so the on-screen keyboard never traps list navigation.
 */
@Composable
private fun SettingTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isSecret: Boolean = false,
) {
    var editing by remember { mutableStateOf(false) }
    val display = when {
        value.isBlank() -> "Not set — press to enter"
        isSecret -> "•".repeat(value.length.coerceIn(4, 12))
        else -> value
    }
    AppListItem(
        selected = false,
        onClick = { editing = true },
        headlineContent = { Text(label) },
        supportingContent = { Text(display) },
        modifier = Modifier.fillMaxWidth(0.85f),
    )
    if (editing) {
        EditFieldDialog(
            title = label,
            initial = value,
            isSecret = isSecret,
            onConfirm = { onValueChange(it); editing = false },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun EditFieldDialog(
    title: String,
    initial: String,
    isSecret: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        var text by remember { mutableStateOf(initial) }
        val fieldFocus = remember { FocusRequester() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (isSecret) KeyboardType.Password else KeyboardType.Uri),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .focusRequester(fieldFocus),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton(onClick = { onConfirm(text) }) { Text("Save") }
                AppButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
        LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
    }
}
