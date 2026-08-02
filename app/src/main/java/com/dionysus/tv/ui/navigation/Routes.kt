package com.dionysus.tv.ui.navigation

import android.net.Uri

/** Central place for navigation routes and their argument builders. */
object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"

    const val DETAIL = "detail/{mediaId}"
    const val STREAMS = "streams/{mediaId}?season={season}&episode={episode}"
    const val PLAYER = "player?url={url}&title={title}&progressId={progressId}"

    fun detail(mediaId: String): String = "detail/${Uri.encode(mediaId)}"

    fun streams(mediaId: String, season: Int? = null, episode: Int? = null): String =
        "streams/${Uri.encode(mediaId)}?season=${season ?: -1}&episode=${episode ?: -1}"

    fun player(url: String, title: String, progressId: String? = null): String =
        "player?url=${Uri.encode(url)}&title=${Uri.encode(title)}&progressId=${Uri.encode(progressId.orEmpty())}"
}

/** Top-level destinations shown in the navigation rail. */
enum class TopLevelDestination(val route: String, val label: String) {
    HOME(Routes.HOME, "Home"),
    SEARCH(Routes.SEARCH, "Search"),
    DOWNLOADS(Routes.DOWNLOADS, "Downloads"),
    SETTINGS(Routes.SETTINGS, "Settings"),
}
