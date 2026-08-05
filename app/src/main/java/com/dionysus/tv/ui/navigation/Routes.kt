package com.dionysus.tv.ui.navigation

import android.net.Uri

/** Central place for navigation routes and their argument builders. */
object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val LIVE_TV = "livetv"
    const val LIVE_PLAYER = "liveplayer"
    const val MOVIES = "movies"
    const val SEARCH = "search"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"

    const val DETAIL = "detail/{mediaId}"
    const val STREAMS = "streams/{mediaId}?season={season}&episode={episode}"
    const val PLAYER =
        "player?url={url}&title={title}&progressId={progressId}&poster={poster}&backdrop={backdrop}"

    fun detail(mediaId: String): String = "detail/${Uri.encode(mediaId)}"

    fun streams(mediaId: String, season: Int? = null, episode: Int? = null): String =
        "streams/${Uri.encode(mediaId)}?season=${season ?: -1}&episode=${episode ?: -1}"

    fun player(
        url: String,
        title: String,
        progressId: String? = null,
        poster: String? = null,
        backdrop: String? = null,
    ): String =
        "player?url=${Uri.encode(url)}&title=${Uri.encode(title)}" +
            "&progressId=${Uri.encode(progressId.orEmpty())}" +
            "&poster=${Uri.encode(poster.orEmpty())}&backdrop=${Uri.encode(backdrop.orEmpty())}"
}

/**
 * Top-level destinations shown in the navigation rail. Movies (VOD) is
 * intentionally not here — it's reached from the "Movies (VOD)" shortcut at the
 * top of the Live TV category rail — but Routes.MOVIES still exists for it.
 */
enum class TopLevelDestination(val route: String, val label: String) {
    HOME(Routes.HOME, "Home"),
    LIVE_TV(Routes.LIVE_TV, "Live TV"),
    SEARCH(Routes.SEARCH, "Search"),
    DOWNLOADS(Routes.DOWNLOADS, "Downloads"),
    SETTINGS(Routes.SETTINGS, "Settings"),
}
