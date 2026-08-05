@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.dionysus.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import android.net.Uri
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
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.setDownloadFolder(uri.toString())
        }
    }

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
                SettingTextField(label = "EPG URL — optional (leave blank for provider default)", value = state.xtreamEpg, onValueChange = viewModel::setXtreamEpg)
                AppButton(onClick = viewModel::addXtreamPlaylist) { Text("Add Xtream account") }
            }
        }
        items(state.playlists, key = { it.id }) { playlist ->
            Row(
                modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        text = "${playlist.name}  •  ${if (playlist.kind == "xtream") "Xtream Codes" else "M3U"}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // Show the saved details so they can be viewed/copied later.
                    if (playlist.kind == "xtream") {
                        PlaylistDetail("Server", playlist.host)
                        PlaylistDetail("Username", playlist.username)
                        PlaylistDetail("Password", playlist.password)
                        PlaylistDetail("EPG URL", playlist.epgUrl)
                    } else {
                        PlaylistDetail("M3U URL", playlist.url)
                        if (playlist.epgUrl.isNotBlank()) PlaylistDetail("EPG URL", playlist.epgUrl)
                    }
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(onClick = { viewModel.editPlaylist(playlist) }) { Text("Edit") }
                    AppButton(onClick = { viewModel.removePlaylist(playlist.id) }) { Text("Remove") }
                }
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth(0.85f).padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val currentTzLabel = TIMEZONES.firstOrNull { it.first == state.guideTimeZone }?.second
                    ?: "Device default"
                Text(
                    "Guide timezone: $currentTzLabel",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Pick the timezone the guide should show times in. Choose your own zone (e.g. Pacific) if the device clock is set to a different one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppButton(onClick = {
                    val idx = TIMEZONES.indexOfFirst { it.first == state.guideTimeZone }
                    val next = TIMEZONES[(idx + 1).mod(TIMEZONES.size)]
                    viewModel.setGuideTimeZone(next.first)
                }) { Text("Timezone: $currentTzLabel") }

                ToggleRow(
                    label = "Auto guide time (use provider clock)",
                    enabled = state.autoGuideTime,
                    onToggle = viewModel::setAutoGuideTime,
                )
                Text(
                    "On: the guide's NOW is taken from your provider's server clock, so it's correct even if this device's clock/timezone is wrong. Turn off to set the correction manually below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!state.autoGuideTime) {
                    Text(
                        "Clock correction: ${formatOffset(state.epgOffsetMinutes)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "If NOW sits on the wrong show, shift the current-time marker until it lands on what's actually airing (e.g. +8h if the guide thinks it's 6 AM but it's really 2 PM).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(onClick = { viewModel.adjustEpgOffset(-60) }) { Text("−1h") }
                        AppButton(onClick = { viewModel.adjustEpgOffset(-30) }) { Text("−30m") }
                        AppButton(onClick = { viewModel.adjustEpgOffset(30) }) { Text("+30m") }
                        AppButton(onClick = { viewModel.adjustEpgOffset(60) }) { Text("+1h") }
                    }
                }

                LiveDefaultCategoryPicker(
                    categories = state.liveCategories,
                    selected = state.liveDefaultCategory,
                    onSelect = viewModel::setLiveDefaultCategory,
                )
            }
        }

        // ---- Downloads -----------------------------------------------------
        item { SectionHeader("Downloads") }
        item {
            Column(
                modifier = Modifier.fillMaxWidth(0.85f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val current = state.downloadFolder
                Text(
                    text = "Saves to: " + (current?.let { folderDisplayName(it) } ?: "App storage (default)"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Pick any folder — internal, USB, SD, or a network drive. Pause/resume keeps working.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppButton(onClick = { folderPicker.launch(null) }) { Text("Choose folder") }
                    if (current != null) {
                        AppButton(onClick = { viewModel.setDownloadFolder(null) }) { Text("Use app storage") }
                    }
                }
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
        item {
            val options = listOf(
                "TRENDING" to "Trending",
                "POPULAR_MOVIES" to "Popular Movies",
                "POPULAR_SHOWS" to "Popular Shows",
                "TOP_RATED_MOVIES" to "Top Rated Movies",
                "MY_LIST" to "My List",
            )
            val currentLabel = options.firstOrNull { it.first == state.featuredSource }?.second ?: "Trending"
            Row(
                modifier = Modifier.fillMaxWidth(0.9f).padding(bottom = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text("Featured banner", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("Shown in the big carousel at the top of Home", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AppButton(onClick = {
                    val idx = options.indexOfFirst { it.first == state.featuredSource }
                    val next = options[(idx + 1).mod(options.size)]
                    viewModel.setFeaturedSource(next.first)
                }) { Text(currentLabel) }
            }
        }
        item {
            Text(
                "Toggle rows on/off and use ↑ ↓ to reorder. Add-on catalogs appear here too.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(0.9f).padding(bottom = 4.dp),
            )
        }
        items(state.homeRows, key = { it.id }) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 2.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ToggleRow(
                        label = row.title + if (!row.enabled) "  (hidden)" else "",
                        enabled = row.enabled,
                        onToggle = { viewModel.toggleHomeRow(row.id, it) },
                    )
                }
                AppButton(onClick = { viewModel.moveHomeRowUp(row.id) }) { Text("↑") }
                Spacer(Modifier.width(8.dp))
                AppButton(onClick = { viewModel.moveHomeRowDown(row.id) }) { Text("↓") }
            }
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

/**
 * Expandable picker for the category Live TV opens on. Collapsed it shows the
 * current choice; expanded it lists every channel category (plus "All Channels")
 * in a scrollable list so any one can be pinned as the landing page.
 */
@Composable
private fun LiveDefaultCategoryPicker(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = selected.ifBlank { "All Channels" }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Live TV opens on",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Choose the category Live TV lands on when you open it (e.g. US Movies). \"All Channels\" shows everything.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Opens on: $currentLabel  (tap to close)" else "Opens on: $currentLabel")
        }
        if (expanded) {
            if (categories.isEmpty()) {
                Text(
                    "Open Live TV once so its categories can load, then come back here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val options = listOf("" to "All Channels") + categories.map { it to it }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(options, key = { it.first.ifBlank { "__all__" } }) { (value, label) ->
                        AppListItem(
                            selected = value == selected,
                            onClick = { onSelect(value); expanded = false },
                            headlineContent = { Text(label, maxLines = 1) },
                        )
                    }
                }
            }
        }
    }
}

/** Curated timezone choices for the guide (id to label). "" = device default. */
private val TIMEZONES: List<Pair<String, String>> = listOf(
    "" to "Device default",
    "America/Los_Angeles" to "Pacific (PT)",
    "America/Denver" to "Mountain (MT)",
    "America/Phoenix" to "Arizona (no DST)",
    "America/Chicago" to "Central (CT)",
    "America/New_York" to "Eastern (ET)",
    "America/Anchorage" to "Alaska",
    "Pacific/Honolulu" to "Hawaii",
    "UTC" to "UTC",
    "Europe/London" to "UK (GMT/BST)",
    "Europe/Paris" to "Central Europe",
)

/** Human-readable guide time offset, e.g. "0", "+1h 30m", "−3h". */
private fun formatOffset(minutes: Int): String {
    if (minutes == 0) return "0 (device time)"
    val sign = if (minutes < 0) "−" else "+"
    val abs = kotlin.math.abs(minutes)
    val h = abs / 60
    val m = abs % 60
    return buildString {
        append(sign)
        if (h > 0) append("${h}h")
        if (m > 0) append(if (h > 0) " ${m}m" else "${m}m")
    }
}

/** A label: value line for a saved playlist, selectable so it can be copied. */
@Composable
private fun PlaylistDetail(label: String, value: String) {
    if (value.isBlank()) return
    Row(modifier = Modifier.padding(top = 2.dp)) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Turn a SAF tree URI into a readable folder name for display. */
private fun folderDisplayName(uriString: String): String = runCatching {
    val decoded = Uri.decode(uriString)
    decoded.substringAfterLast(':').ifBlank { decoded.substringAfterLast('/') }
        .ifBlank { "Selected folder" }
}.getOrDefault("Selected folder")

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
