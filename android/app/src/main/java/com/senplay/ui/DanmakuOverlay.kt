package com.senplay.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senplay.core.DanmakuItem
import com.senplay.core.DanmakuMode
import kotlin.math.roundToInt

private data class VisibleDanmaku(val item: DanmakuItem, val lane: Int)

/**
 * Danmaku surface.
 *
 * Comments are bucketed into lanes; a lane frees up once its previous comment has
 * fully entered the screen. Cost is proportional to what is on screen right now,
 * not to the whole track.
 */
@Composable
fun DanmakuOverlay(
    items: List<DanmakuItem>,
    currentTime: Double,
    fontSize: Double,
    speed: Double,
    area: Double,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val travel = 6.0 * speed
    val window = 12.0 * speed
    val laneCount = maxOf((area * 20).toInt(), 3)
    val density = LocalDensity.current

    val visible = remember(items, currentTime, speed, area) {
        val lanes = LongArray(laneCount) { Long.MIN_VALUE }
        val lastText: Array<String?> = arrayOfNulls<String>(laneCount)
        val result = mutableListOf<VisibleDanmaku>()

        for (item in items) {
            if (item.time > currentTime || item.time <= currentTime - window) continue
            if (item.mode == DanmakuMode.TOP || item.mode == DanmakuMode.BOTTOM) {
                result += VisibleDanmaku(item, if (item.mode == DanmakuMode.TOP) 0 else laneCount - 1)
                continue
            }
            val slot = lanes.indices.firstOrNull { index ->
                lanes[index] <= (item.time * 1000).toLong() && lastText[index] != item.text
            }
            if (slot != null) {
                lanes[slot] = ((item.time + travel) * 1000).toLong()
                lastText[slot] = item.text
                result += VisibleDanmaku(item, slot)
            }
        }
        result
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val lineHeight = fontSize + 10.0

        Box(modifier = Modifier.fillMaxSize()) {
            visible.forEach { entry ->
                val elapsed = currentTime - entry.item.time
                if (elapsed < 0) return@forEach
                val progress = (elapsed / travel).coerceIn(0.0, 1.0)
                if (progress >= 1.0) return@forEach

                val xPx = when (entry.item.mode) {
                    DanmakuMode.TOP, DanmakuMode.BOTTOM -> widthPx / 2f
                    DanmakuMode.REVERSE -> (widthPx * (progress * 1.1f - 0.05f)).toFloat()
                    else -> (widthPx * (1.05 - 1.1 * progress)).toFloat()
                }
                val yDp: Dp = (18.0 + entry.lane * lineHeight).dp
                val yPx: Float = with(density) { yDp.toPx() }

                Text(
                    text = entry.item.text,
                    color = Color(0xFF000000.toInt() or entry.item.color),
                    fontSize = fontSize.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    style = TextStyle(
                        shadow = Shadow(color = Color.Black.copy(alpha = 0.75f), blurRadius = 3f)
                    ),
                    modifier = Modifier.offset {
                        IntOffset(x = xPx.roundToInt(), y = yPx.roundToInt())
                    }
                )
            }
        }
    }
}
