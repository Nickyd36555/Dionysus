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
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.dionysus.tv.ui.navigation.TopLevelDestination

private val RAIL_WIDTH = 104.dp
private var railFocusedOnce = false

class RailController(val setHidden: (Boolean) -> Unit)
val LocalRailController = staticCompositionLocalOf { RailController {} }

@Composable
fun MainScaffold(
    selected: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    var railHidden by remember { mutableStateOf(false) }
    LaunchedEffect(selected) { railHidden = false }
    val controller = remember { RailController { railHidden = it } }
    val homeFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (!railFocusedOnce && selected == TopLevelDestination.HOME) {
            railFocusedOnce = true
            runCatching { homeFocus.requestFocus() }
        }
    }

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
                    .padding(horizontal = 10.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Spacer(Modifier.height(2.dp))
                TopLevelDestination.entries.forEach { dest ->
                    NavRailItem(
                        icon = iconFor(dest),
                        label = dest.label,
                        selected = dest == selected,
                        onClick = { onSelect(dest) },
                        modifier = if (dest == TopLevelDestination.HOME) {
                            Modifier.focusRequester(homeFocus)
                        } else {
                            Modifier
                        },
                    )
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
private fun NavRailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    val background = when {
        focused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    val foreground = if (focused) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier
            .width(84.dp)
            .height(60.dp)
            .clip(shape)
            .background(background)
            .then(
                if (selected && !focused) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                },
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = foreground,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            color = foreground,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

private fun iconFor(dest: TopLevelDestination): ImageVector = when (dest) {
    TopLevelDestination.HOME -> Icons.Default.Home
    TopLevelDestination.MOVIES -> Icons.Default.Movie
    TopLevelDestination.SERIES -> Icons.Default.Tv
    TopLevelDestination.LIVE_TV -> Icons.Default.LiveTv
    TopLevelDestination.SEARCH -> Icons.Default.Search
    TopLevelDestination.DOWNLOADS -> Icons.Default.Download
    TopLevelDestination.SETTINGS -> Icons.Default.Settings
}
