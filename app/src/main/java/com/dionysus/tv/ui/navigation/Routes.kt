package com.dionysus.tv.ui.navigation

import android.net.Uri

/** Top-level destinations shown in the Stremio-style navigation rail. */
enum class TopLevelDestination(val route: String, val label: String) {
    HOME(Routes.HOME, "Home"),
    MOVIES(Routes.MOVIES, "Movies"),
    SERIES(Routes.SERIES, "Series"),
    LIVE_TV(Routes.LIVE_TV, "Live TV"),
    SEARCH(Routes.SEARCH, "Search"),
    DOWNLOADS(Routes.DOWNLOADS, "Library"),
    SETTINGS(Routes.SETTINGS, "Settings"),
}
