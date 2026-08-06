package com.dionysus.tv.ui.theme

import androidx.compose.runtime.Composable
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
@Composable
fun DionysusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DionysusColorScheme,
        typography = DionysusTypography,
        content = content,
    )
}
