package com.dionysus.tv.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
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

/** Root theme for the app. TV apps are dark-first, so there is only one scheme. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DionysusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DionysusColorScheme,
        typography = DionysusTypography,
    ) {
        // Disable the touch-oriented overscroll stretch/bounce — under D-pad it just
        // makes lists (the guide, Home, Search…) rubber-band. Applies app-wide.
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            content()
        }
    }
}
