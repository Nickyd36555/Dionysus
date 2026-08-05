package com.dionysus.tv.player

import android.app.ActivityManager
import android.content.Context

/**
 * Picks the best playback quality a given device can actually sustain, so we get
 * the sharpest picture without stutter.
 *
 * Video always decodes with hardware acceleration first (falling back to
 * libvlc-all's software decoders when the SoC can't handle a codec), because
 * hardware decode is the *lightest* path — that's on for every device. What we
 * scale by device tier is the CPU-heavy post-processing:
 *
 *  - HIGH  (>=6 cores, >=3 GB RAM): motion-compensated deinterlacing (yadif2x)
 *          — the smoothest broadcast/SD-to-4K result.
 *  - MEDIUM(>=4 cores, >=2 GB RAM): standard yadif deinterlacing.
 *  - LOW   (everything else):       cheap linear deinterlacing so weak boxes
 *          stay smooth.
 *
 * Deinterlacing is set to "auto" (-1) in every tier, so it only engages on
 * genuinely interlaced sources (most live TV) and leaves progressive video
 * untouched.
 */
object PlaybackTuning {

    enum class Tier { LOW, MEDIUM, HIGH }

    fun tier(context: Context): Tier {
        val cores = Runtime.getRuntime().availableProcessors()
        val ramGb = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            info.totalMem / (1024.0 * 1024.0 * 1024.0)
        }.getOrDefault(2.0)
        return when {
            cores >= 6 && ramGb >= 3.0 -> Tier.HIGH
            cores >= 4 && ramGb >= 2.0 -> Tier.MEDIUM
            else -> Tier.LOW
        }
    }

    /** LibVLC init options tuned to the device tier. */
    fun libVlcOptions(tier: Tier, live: Boolean): ArrayList<String> {
        val opts = arrayListOf(
            "--network-caching=3000",
            "--file-caching=3000",
            "--live-caching=3000",
            "--no-drop-late-frames",
            "--no-skip-frames",
            "--no-mediacodec-dr",
            "--no-omxil-dr",
            "--audio-time-stretch",
            "--audio-resampler=soxr",   // high-quality audio resampling
            "--deinterlace=-1",         // auto: only engages on interlaced sources
        )
        when (tier) {
            Tier.HIGH -> opts += "--deinterlace-mode=yadif2x"
            Tier.MEDIUM -> opts += "--deinterlace-mode=yadif"
            Tier.LOW -> opts += "--deinterlace-mode=linear"
        }
        return opts
    }
}
