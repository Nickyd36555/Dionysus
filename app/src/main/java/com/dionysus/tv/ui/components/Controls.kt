@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.dionysus.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import com.dionysus.tv.ui.theme.DionysusGold
import com.dionysus.tv.ui.theme.DionysusGoldBright
import com.dionysus.tv.ui.theme.DionysusGoldDeep
import com.dionysus.tv.ui.theme.DionysusOnGold

/*
 * Touch- AND D-pad-friendly controls.
 *
 * Compose-for-TV's own clickable Surface/Card/Button/ListItem respond only to
 * D-pad center, not taps — which makes the app impossible to drive on a touch
 * emulator. These wrappers use foundation `Modifier.clickable`, which fires on
 * BOTH a tap and a focused D-pad/Enter press, and keeps a visible focus
 * highlight for remote navigation. Styling mirrors the TV components.
 *
 * Shared "Wine & Gold, sharp & architectural" tokens: small corner radius, thin
 * hairline borders, gold fills with dark text.
 */

private val CornerSharp = 3.dp
private val Hairline = 1.dp
private val HairlineFocus = 1.5.dp

/** Filled primary button. Drop-in for tv-material3 `Button`. */
@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(CornerSharp)
    // Embossed metal: a vertical gradient (bright sheen on top → deep gold below)
    // reads as a raised, 3D plate. Focus brightens + lifts; pressed inverts the
    // gradient so it looks pushed in.
    val fill = when {
        !enabled -> Brush.verticalGradient(
            listOf(DionysusGold.copy(alpha = 0.4f), DionysusGoldDeep.copy(alpha = 0.4f)),
        )
        pressed -> Brush.verticalGradient(listOf(DionysusGoldDeep, DionysusGold))
        focused -> Brush.verticalGradient(listOf(DionysusGoldBright, DionysusGold))
        else -> Brush.verticalGradient(listOf(DionysusGold, DionysusGoldDeep))
    }
    val bevel = Brush.verticalGradient(listOf(DionysusGoldBright, DionysusGoldDeep))
    Row(
        modifier = modifier
            .shadow(
                elevation = if (!enabled) 0.dp else if (focused) 12.dp else 5.dp,
                shape = shape,
                spotColor = DionysusGold,
                ambientColor = Color.Black,
            )
            .clip(shape)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null) { onClick() }
            .background(fill)
            .border(1.5.dp, bevel, shape) // beveled metallic frame (bright top, dark bottom)
            .padding(2.5.dp)
            .border(Hairline, DionysusOnGold.copy(alpha = 0.30f), RoundedCornerShape(2.dp)) // engraved inner line
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onPrimary) {
            content()
        }
    }
}

/** Rounded container row. Drop-in for a clickable tv-material3 `Surface`. */
@Composable
fun AppSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(CornerSharp),
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // Subtly raised panel: a soft top-to-bottom gradient plus a gold hairline at
    // rest, upgrading to a beveled gold frame and a lift shadow on focus.
    val panel = Brush.verticalGradient(
        listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface),
    )
    val frame = if (focused) {
        Modifier.border(HairlineFocus, Brush.verticalGradient(listOf(DionysusGoldBright, DionysusGoldDeep)), shape)
    } else {
        Modifier.border(Hairline, MaterialTheme.colorScheme.border, shape)
    }
    Column(
        modifier = modifier
            .shadow(if (focused) 8.dp else 0.dp, shape, spotColor = DionysusGold, ambientColor = Color.Black)
            .clip(shape)
            .background(panel)
            .then(frame)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}

/** Selectable list row. Drop-in for tv-material3 `ListItem` (subset of params). */
@Composable
fun AppListItem(
    selected: Boolean,
    onClick: () -> Unit,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(CornerSharp)
    val bg = when {
        focused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.surfaceVariant
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    // A thin gold marker on the selected (not-focused) row, so the current choice
    // stays legible even when focus is elsewhere.
    val fg = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .then(
                if (selected && !focused) Modifier.border(Hairline, MaterialTheme.colorScheme.primary, shape)
                else Modifier,
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            leadingContent?.invoke()
            Column(modifier = Modifier.weight(1f)) {
                headlineContent()
                supportingContent?.invoke()
            }
        }
    }
}
