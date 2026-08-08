package com.dionysus.tv.data.settings

import kotlinx.serialization.Serializable

/**
 * A customizable quick-access tile on the Home screen (the Disney/Pixar/Marvel-style
 * strip). Tapping a tile opens Search pre-filled with [query], so a tile is just a
 * saved shortcut to a category, studio, genre, or franchise.
 */
@Serializable
data class HomeTile(
    val id: String,
    val label: String,
    val query: String,
)
