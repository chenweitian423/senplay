package com.senplay.player

import android.media.audiofx.LoudnessEnhancer
import androidx.media3.exoplayer.analytics.AnalyticsListener

/**
 * Counters the service collects on behalf of the UI.
 *
 * A MediaController sees player state but not the decoder-level numbers — bitrates
 * and dropped frames come from ExoPlayer's analytics listener, and the audio session
 * id it needs for the gain stage only exists on the real player. Both live in the
 * service and are published here for the activity to read.
 */
object PlaybackStats {
    @Volatile var videoBitrate: Int = 0
    @Volatile var audioBitrate: Int = 0
    @Volatile var droppedFrames: Int = 0
    @Volatile var width: Int = 0
    @Volatile var height: Int = 0
    /** The overhead shows a frame rate beside the clock; the input format carries it. */
    @Volatile var frameRate: Float = 0f
    /** Bits a second as the loader estimates them — the overlay's network rate. */
    @Volatile var bandwidth: Long = 0
    /** The encoder's mime type, e.g. "video/hevc". */
    @Volatile var sampleMimeType: String = ""
    /** C.COLOR_TRANSFER_* of the video track; PQ and HLG mean HDR. */
    @Volatile var colorTransfer: Int = 0
    /**
     * Whether the decoder Media3 picked runs in hardware. Android names it, and
     * the platform's software decoders all carry one of two prefixes — so this
     * reading exists only on this side: KSPlayer keeps its decoder map private.
     */
    @Volatile var hardwareDecoded: Boolean = false

    fun reset() {
        videoBitrate = 0
        audioBitrate = 0
        droppedFrames = 0
        width = 0
        height = 0
        frameRate = 0f
        bandwidth = 0
        sampleMimeType = ""
        colorTransfer = 0
        hardwareDecoded = false
    }
}

/** Wires an ExoPlayer into [PlaybackStats]. */
fun analyticsListener(): AnalyticsListener = object : AnalyticsListener {
    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: androidx.media3.common.Format,
        decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
    ) {
        PlaybackStats.videoBitrate = format.bitrate
        PlaybackStats.frameRate = format.frameRate ?: 0f
        PlaybackStats.sampleMimeType = format.sampleMimeType ?: ""
        PlaybackStats.colorTransfer = format.colorInfo?.colorTransfer ?: 0
    }

    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long
    ) {
        PlaybackStats.hardwareDecoded = !decoderName.startsWith("c2.android.") &&
            !decoderName.startsWith("OMX.google.")
    }

    override fun onBandwidthEstimate(
        eventTime: AnalyticsListener.EventTime,
        totalLoadTimeMs: Int,
        totalBytesLoaded: Long,
        bitrateEstimate: Long
    ) {
        // Bits per second, the same unit the loader reports.
        PlaybackStats.bandwidth = bitrateEstimate
    }

    override fun onAudioInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: androidx.media3.common.Format,
        decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
    ) {
        PlaybackStats.audioBitrate = format.bitrate
    }

    override fun onDroppedVideoFrames(
        eventTime: AnalyticsListener.EventTime,
        droppedFrames: Int,
        elapsedMs: Long
    ) {
        PlaybackStats.droppedFrames = droppedFrames
    }

    override fun onVideoSizeChanged(
        eventTime: AnalyticsListener.EventTime,
        videoSize: androidx.media3.common.VideoSize
    ) {
        PlaybackStats.width = videoSize.width
        PlaybackStats.height = videoSize.height
    }
}

/**
 * Android's own gain stage. The counterpart of the iOS volume filter, and what
 * "音量到达 100% 后继续放大" actually means.
 */
/** "音频增强倍数": LoudnessEnhancer takes millibels, and 200% is twice the amplitude
 * — 20·log10(2)·100 ≈ 602, which is the +6 dB this used to hard-code. */
private fun percentToMillibels(percent: Float): Int =
    (20.0 * kotlin.math.log10((percent / 100.0).coerceAtLeast(0.01)) * 100).toInt()

class GainStage(sessionId: Int, percent: Float = 200f) {
    private val enhancer: LoudnessEnhancer? = runCatching {
        LoudnessEnhancer(sessionId).apply {
            setTargetGain(percentToMillibels(percent))
            enabled = true
        }
    }.getOrNull()

    fun release() {
        runCatching {
            enhancer?.enabled = false
            enhancer?.release()
        }
    }
}
