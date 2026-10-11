package com.senplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Live counters reported by the player. Values arrive from the analytics listener
 * rather than being polled, so anything the platform does not report simply stays
 * at zero instead of showing a guess.
 */
data class OsdStats(
    val videoBitrate: Int = 0,
    val audioBitrate: Int = 0,
    val dropped: Int = 0,
    val width: Int = 0,
    val height: Int = 0
)

@Composable
fun OsdOverlay(
    stats: OsdStats,
    positionMs: Long,
    bufferedMs: Long,
    modifier: Modifier = Modifier
) {
    val lines = buildList {
        if (stats.width > 0 && stats.height > 0) add("画面 ${stats.width}x${stats.height}")
        if (stats.videoBitrate > 0) add("视频 ${bitrate(stats.videoBitrate)}")
        if (stats.audioBitrate > 0) add("音频 ${bitrate(stats.audioBitrate)}")
        if (stats.dropped > 0) add("丢帧 ${stats.dropped}")
        add("缓冲 ${(bufferedMs - positionMs).coerceAtLeast(0) / 1000}s")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 54.dp, end = 16.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(8.dp)
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun bitrate(bits: Int): String = when {
    bits >= 1_000_000 -> String.format("%.2f Mbps", bits / 1_000_000.0)
    bits >= 1_000 -> String.format("%.0f kbps", bits / 1_000.0)
    else -> "$bits bps"
}
