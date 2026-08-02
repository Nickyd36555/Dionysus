package com.dionysus.tv.data.local

/** Lifecycle states for a local download. */
enum class DownloadStatus {
    QUEUED,
    RESOLVING,   // asking a debrid provider for a direct link
    DOWNLOADING,
    COMPLETED,
    FAILED,
    PAUSED;

    val isTerminal: Boolean get() = this == COMPLETED || this == FAILED
}
