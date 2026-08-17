package com.dionysus.tv.ui.theme

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val DionysusColorScheme = darkColorScheme(
    primary = DionysusGold,
    onPrimary = DionysusOnGold,
    primaryContainer = DionysusGoldBright,
    secondary = DionysusGoldBright,
    onSecondary = DionysusOnGold,
    background = DionysusBackground,
    onBackground = DionysusOnSurface,
    surface = DionysusSurface,
    onSurface = DionysusOnSurface,
    surfaceVariant = DionysusSurfaceVariant,
    onSurfaceVariant = DionysusOnSurfaceVariant,
    border = DionysusOutline,
    error = DionysusError,
    onError = Color.White,
)

/**
 * Focus auto-scroll that moves the list when D-pad focus changes. The framework
 * default uses a spring, which overshoots and settles — that's the "bounce" when
 * scrolling down rows. A short tween scrolls smoothly with no spring/overshoot.
 */
@OptIn(ExperimentalFoundationApi::class)
private val SmoothBringIntoView = object : BringIntoViewSpec {
    override val scrollAnimationSpec = tween<Float>(durationMillis = 150)

    // Minimal-scroll: if the item is already visible, don't move; otherwise scroll
    // just enough to bring its nearest edge into view (same as the framework default,
    // reimplemented because the default instance is internal).
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val leadingEdge = offset
        val trailingEdge = offset + size
        return when {
            leadingEdge >= 0f && trailingEdge <= containerSize -> 0f
            leadingEdge < 0f && trailingEdge > containerSize -> 0f
            kotlin.math.abs(leadingEdge) < kotlin.math.abs(trailingEdge - containerSize) -> leadingEdge
            else -> trailingEdge - containerSize
        }
    }
}

/** Root theme for the app. TV apps are dark-first, so there is only one scheme. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DionysusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DionysusColorScheme,
        typography = DionysusTypography,
    ) {
        // - Overscroll null: kill the touch-style rubber-band stretch under D-pad.
        // - BringIntoView tween: kill the springy overshoot when focus scrolls a list.
        CompositionLocalProvider(
            LocalOverscrollConfiguration provides null,
            LocalBringIntoViewSpec provides SmoothBringIntoView,
        ) {
            content()
        }
    }
}
