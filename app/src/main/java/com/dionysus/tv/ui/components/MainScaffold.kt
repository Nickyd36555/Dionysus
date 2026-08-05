@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.dionysus.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import coil.compose.AsyncImage
import com.dionysus.tv.R
import com.dionysus.tv.ui.navigation.TopLevelDestination

private val RAIL_WIDTH = 76.dp

/** Lets a top-level screen hide the nav rail (e.g. the Live TV guide going full-screen). */
class RailController(val setHidden: (Boolean) -> Unit)

val LocalRailController = staticCompositionLocalOf { RailController {} }

/**
 * The persistent left navigation rail. It is a fixed-width, icon-only rail (no
 * expand/collapse and no width animation) — this is deliberate: earlier
 * focus-driven expansion reflowed content and made the rail flicker open/closed
 * when navigating. A static rail is rock-steady and matches the reference UX.
 *
 * A screen may hide the rail entirely (for an immersive full-screen guide) via
 * [LocalRailController]; the rail is always restored when the destination changes.
 */
@Composable
fun MainScaffold(
    selected: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    var railHidden by remember { mutableStateOf(false) }
    // Any navigation restores the rail so it can never get stuck hidden.
    LaunchedEffect(selected) { railHidden = false }
    val controller = remember { RailController { railHidden = it } }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (!railHidden) {
            Column(
                modifier = Modifier
                    .width(RAIL_WIDTH)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                TopLevelDestination.entries.forEach { dest ->
                    if (dest == TopLevelDestination.HOME) {
                        // Home shows the app's own logo instead of a generic house.
                        NavRailImage(
                            imageRes = R.mipmap.ic_launcher_round,
                            label = dest.label,
                            selected = dest == selected,
                            onClick = { onSelect(dest) },
                        )
                    } else {
                        NavRailIcon(
                            icon = iconFor(dest),
                            label = dest.label,
                            selected = dest == selected,
                            onClick = { onSelect(dest) },
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            CompositionLocalProvider(LocalRailController provides controller) {
                content()
            }
        }
    }
}

@Composable
private fun NavRailImage(
    imageRes: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(shape)
            .then(
                when {
                    focused -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                    selected -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), shape)
                    else -> Modifier
                },
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        // Use Coil (not painterResource) so adaptive-icon mipmaps render instead
        // of crashing — painterResource can't parse <adaptive-icon> XML.
        AsyncImage(
            model = imageRes,
            contentDescription = label,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
        )
    }
}

@Composable
private fun NavRailIcon(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    val bg = when {
        focused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    val fg = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(shape)
            .background(bg)
            .then(if (selected && !focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(26.dp))
    }
}

private fun iconFor(dest: TopLevelDestination): ImageVector = when (dest) {
    TopLevelDestination.HOME -> Icons.Default.Home
    TopLevelDestination.LIVE_TV -> Icons.Default.LiveTv
    TopLevelDestination.SEARCH -> Icons.Default.Search
    TopLevelDestination.DOWNLOADS -> Icons.Default.Download
    TopLevelDestination.SETTINGS -> Icons.Default.Settings
}
