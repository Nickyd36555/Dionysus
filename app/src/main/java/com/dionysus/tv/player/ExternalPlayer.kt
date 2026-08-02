package com.dionysus.tv.player

/**
 * Third-party players Dionysus can hand a stream off to. "internal" uses the
 * built-in Media3 player. Package fallbacks cover free/pro variants.
 */
enum class ExternalPlayer(
    val id: String,
    val displayName: String,
    val packages: List<String>,
) {
    INTERNAL("internal", "Built-in Player", emptyList()),
    VLC("vlc", "VLC", listOf("org.videolan.vlc")),
    NPLAYER("nplayer", "nPlayer", listOf("com.newin.nplayer.pro", "com.newin.nplayer")),
    MX_PLAYER("mxplayer", "MX Player", listOf("com.mxtech.videoplayer.pro", "com.mxtech.videoplayer.ad")),
    JUST_PLAYER("justplayer", "Just Player", listOf("com.brouken.player")),
    KODI("kodi", "Kodi", listOf("org.xbmc.kodi")),
    ;

    val isInternal: Boolean get() = this == INTERNAL

    companion object {
        fun fromId(id: String): ExternalPlayer = entries.firstOrNull { it.id == id } ?: INTERNAL
    }
}
