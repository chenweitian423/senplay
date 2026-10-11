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

    fun reset() {
        videoBitrate = 0
        audioBitrate = 0
        droppedFrames = 0
        width = 0
        height = 0
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
class GainStage(sessionId: Int) {
    private val enhancer: LoudnessEnhancer? = runCatching {
        LoudnessEnhancer(sessionId).apply {
            setTargetGain(600) // +6 dB
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
