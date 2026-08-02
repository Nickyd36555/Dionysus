package com.dionysus.tv.player

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands a resolved stream URL to an external player via an ACTION_VIEW intent,
 * using each player's known extras so titles/resume points carry across.
 * Returns false if the target app isn't installed so the caller can fall back
 * to the internal player.
 */
@Singleton
class PlayerLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isInstalled(player: ExternalPlayer): Boolean {
        if (player.isInternal) return true
        return resolvePackage(player) != null
    }

    /** Players (besides the internal one) that are actually installed. */
    fun installedExternalPlayers(): List<ExternalPlayer> =
        ExternalPlayer.entries.filter { !it.isInternal && isInstalled(it) }

    fun launch(
        player: ExternalPlayer,
        url: String,
        title: String,
        resumePositionMs: Long = 0,
    ): Boolean {
        val pkg = resolvePackage(player) ?: return false
        val uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("title", title)
            applyPlayerExtras(player, title, resumePositionMs)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            false
        }
    }

    private fun Intent.applyPlayerExtras(player: ExternalPlayer, title: String, positionMs: Long) {
        when (player) {
            ExternalPlayer.VLC -> {
                putExtra("title", title)
                if (positionMs > 0) putExtra("from_start", false)
                if (positionMs > 0) putExtra("position", positionMs)
            }
            ExternalPlayer.MX_PLAYER -> {
                putExtra("title", title)
                if (positionMs > 0) putExtra("position", positionMs.toInt())
                putExtra("return_result", true)
            }
            ExternalPlayer.NPLAYER, ExternalPlayer.JUST_PLAYER -> {
                putExtra("title", title)
                if (positionMs > 0) putExtra("position", positionMs)
            }
            else -> Unit
        }
    }

    private fun resolvePackage(player: ExternalPlayer): String? {
        val pm = context.packageManager
        return player.packages.firstOrNull { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }
}
