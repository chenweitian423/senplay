package com.senplay.core

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache

/**
 * One frame per local file, for the still on a list row.
 *
 * "视频预览图时间" says where in the file the frame comes from, as a percentage, and
 * the reference app spells out that 0 means "do not generate one"
 * (`0NotGeneratingPreviewImage`) — so zero returns null rather than a frame at the
 * very start.
 *
 * Frames are cached by uri and percentage: opening a file and seeking into it is not
 * cheap, and scrolling back to the top would otherwise redo it for every row.
 */
object Thumbnails {

    private val cache = LruCache<String, Bitmap>(64)

    suspend fun frame(context: Context, uri: String, percent: Int): Bitmap? {
        if (percent <= 0) return null
        val key = "$uri#$percent"
        cache.get(key)?.let { return it }

        return io {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(uri))
                val durationMs = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?: return@io null
                if (durationMs <= 0L) return@io null
                val frame = retriever.getFrameAtTime(
                    durationMs * percent * 1000L / 100L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                frame?.also { cache.put(key, it) }
            } catch (t: Throwable) {
                // A file the platform cannot read is a row without a still, not a crash.
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
    }
}
