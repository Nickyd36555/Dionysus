@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.dionysus.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.dionysus.tv.R
import com.dionysus.tv.ui.navigation.TopLevelDestination
import kotlinx.coroutines.delay

private val RAIL_COLLAPSED = 76.dp
private val RAIL_EXPANDED = 232.dp

/**
 * The persistent left navigation rail. It stays collapsed to icons and expands
 * to labels when focused. The expanded rail is drawn as an OVERLAY on top of the
 * content (the content keeps a fixed collapsed-width margin and never reflows),
 * which is what stops the focus from oscillating and the rail from flickering
 * open/closed when you click things in the content area. Collapse is debounced
 * so brief focus hops (e.g. during navigation) don't snap it shut.
 */
@Composable
fun MainScaffold(
    selected: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    var rawFocus by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(rawFocus) {
        if (rawFocus) {
            expanded = true
        } else {
            delay(160)
            expanded = false
        }
    }
    val railWidth by animateDpAsState(
        targetValue = if (expanded) RAIL_EXPANDED else RAIL_COLLAPSED,
        animationSpec = tween(180),
        label = "railWidth",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Content reserves only the collapsed width, so expansion never reflows it.
        Row(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.width(RAIL_COLLAPSED))
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                content()
            }
        }

        // The rail itself, overlaid on top of the reserved margin / content.
        Column(
            modifier = Modifier
                .width(railWidth)
                .fillMaxHeight()
                .zIndex(1f)
                .background(MaterialTheme.colorScheme.surface)
                .onFocusChanged { rawFocus = it.hasFocus }
                .padding(horizontal = 12.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            TopLevelDestination.entries.forEach { dest ->
                NavRailItem(
                    icon = iconFor(dest),
                    label = dest.label,
                    selected = dest == selected,
                    expanded = expanded,
                    onClick = { onSelect(dest) },
                )
            }
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
                Image(
                    painter = painterResource(R.drawable.logo_wordmark),
                    contentDescription = "Dionysus Streaming",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun NavRailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    val bg = when {
        focused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    val fg = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .then(if (selected && !focused) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(vertical = 12.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(26.dp))
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            Text(
                text = label,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

private fun iconFor(dest: TopLevelDestination): ImageVector = when (dest) {
    TopLevelDestination.HOME -> Icons.Default.Home
    TopLevelDestination.LIVE_TV -> Icons.Default.LiveTv
    TopLevelDestination.SEARCH -> Icons.Default.Search
    TopLevelDestination.DOWNLOADS -> Icons.Default.Download
    TopLevelDestination.SETTINGS -> Icons.Default.Settings
}
