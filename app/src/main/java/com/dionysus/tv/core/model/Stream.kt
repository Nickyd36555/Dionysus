package com.dionysus.tv.core.model

/** Rough video quality bucket parsed from a scraper result's title. */
enum class Quality(val label: String, val rank: Int) {
    UHD_4K("4K", 4),
    FHD_1080("1080p", 3),
    HD_720("720p", 2),
    SD("SD", 1),
    UNKNOWN("?", 0);

    companion object {
        /** Best-effort parse of quality from a release title. */
        fun fromTitle(title: String): Quality {
            val t = title.lowercase()
            return when {
                "2160" in t || "4k" in t || "uhd" in t -> UHD_4K
                "1080" in t -> FHD_1080
                "720" in t -> HD_720
                "480" in t || "sd" in t || "cam" in t -> SD
                else -> UNKNOWN
            }
        }
    }
}

/**
 * A candidate stream produced by a [com.dionysus.tv.data.scraper.Scraper].
 * This is *not* yet playable — a magnet/torrent must be resolved through a
 * debrid service, or an already-direct [url] can be played as-is.
 */
data class StreamSource(
    /** Human-readable release title, e.g. "Movie.2024.2160p.WEB-DL". */
    val title: String,
    /** Which scraper produced this result (Torrentio, Orion, ...). */
    val provider: String,
    val quality: Quality = Quality.fromTitle(title),
    val sizeBytes: Long? = null,
    val seeders: Int? = null,
    /** Torrent info-hash, when the source is a magnet/torrent. */
    val infoHash: String? = null,
    val magnetUri: String? = null,
    /** A directly playable URL, when the scraper already returns one. */
    val url: String? = null,
    /** For multi-file torrents, the index of the video file to select. */
    val fileIndex: Int? = null,
    /** True when a debrid provider reports this hash is already cached. */
    val cachedOn: Set<DebridProvider> = emptySet(),
) {
    val isDirect: Boolean get() = url != null
    val isTorrent: Boolean get() = infoHash != null || magnetUri != null
}

/** A stream that has been resolved to a final, directly playable URL. */
data class ResolvedStream(
    val playbackUrl: String,
    val fileName: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val resolvedBy: DebridProvider? = null,
)
