package com.dionysus.tv.core.model

/** Supported debrid providers. Extend this as new services are added. */
enum class DebridProvider(val displayName: String) {
    REAL_DEBRID("Real-Debrid"),
    PREMIUMIZE("Premiumize"),
}

/** Current authentication / account state for a debrid provider. */
data class DebridAccount(
    val provider: DebridProvider,
    val isConnected: Boolean,
    val username: String? = null,
    /** Unix seconds when premium access expires, if known. */
    val premiumExpiry: Long? = null,
)
