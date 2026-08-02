package com.dionysus.tv.data.debrid

/** Shared helper for identifying video files inside multi-file torrents. */
object VideoExtensions {
    private val EXTENSIONS = setOf(
        "mkv", "mp4", "avi", "mov", "wmv", "flv", "m4v", "mpg", "mpeg", "ts", "webm",
    )

    /** True when [path] ends with a known video extension. */
    fun matches(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in EXTENSIONS
    }

    /** Rough sample-file filter to avoid selecting trailers/samples. */
    fun isSample(path: String): Boolean = "sample" in path.lowercase()
}
