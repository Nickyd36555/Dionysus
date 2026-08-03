package com.dionysus.tv.core.model

/** The kinds of rows the home screen can render. */
enum class HomeRowKind {
    CONTINUE_WATCHING,
    MY_LIST,
    TRENDING,
    POPULAR_MOVIES,
    POPULAR_SHOWS,
    TOP_RATED_MOVIES,
    DOWNLOADS,
    /** A catalog provided by an installed add-on; identified by [HomeRow.param]. */
    ADDON_CATALOG,
    ;

    companion object {
        fun fromName(name: String): HomeRowKind? = entries.firstOrNull { it.name == name }
    }
}

/** A single configured home row, ordered by [position]. */
data class HomeRow(
    val id: String,
    val kind: HomeRowKind,
    val title: String,
    val position: Int,
    val enabled: Boolean,
    val param: String? = null,
)
