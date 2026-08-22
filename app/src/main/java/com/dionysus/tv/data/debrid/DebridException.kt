package com.dionysus.tv.data.debrid

/**
 * A debrid provider refused to serve real content for an account-level reason
 * (premium expired, daily fair-use / usage limit hit, link flagged). The message
 * is user-facing and safe to show directly in the UI. Distinct from a plain
 * "couldn't resolve" (null) so callers can show the real cause instead of a
 * generic "no sources" message — or letting the provider's own error video play.
 */
class DebridException(message: String) : Exception(message)
