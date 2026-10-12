package com.senplay.core

/** A media server entry. Mirrors the URL-scheme contract read out of the analysed app. */
data class ServerConfig(
    val id: String,
    val type: String = "webdav",
    val name: String = "",
    val address: String = "",
    val username: String = "",
    val password: String = "",
    val note: String = "",
    val fallbackAddresses: List<List<String>> = emptyList()
) {
    val isEmbyLike: Boolean get() = type == "emby" || type == "jellyfin" || type == "feiniu"
}

data class MediaEntry(
    val name: String,
    val url: String,
    val isDirectory: Boolean = false,
    val size: Long = 0L,
    val entryId: String? = null,
    val childCount: Int = 0,
    /** Episodes not yet watched, from Emby's UserData. "剩余播放数量" counts items, not time. */
    val unplayedCount: Int = 0,
    /** Last modification, when the source reports it. "文件夹项目显示时间". */
    val modifiedAt: Long = 0L,
    /** Episode number within its season, for the "选集指示器". */
    val episodeNumber: Int = 0,
    /** First few cast names, for "显示演职人员". Empty when the source sends none. */
    val cast: List<String> = emptyList(),
    /** Video dimensions from the first video stream. Zero when the source sends none. */
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Double = 0.0,
    /** "HDR" / "SDR" / "DolbyVision" as the server reports it, empty when absent. */
    val videoRange: String = "",
    val durationTicks: Long = 0L,
    val played: Boolean = false,
    /** Server-reported resume position, used by the poster wall and resume strip. */
    val positionMs: Long = 0L,
    val overview: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val imageUrl: String? = null
) {
    val durationMs: Long get() = durationTicks / 10_000L

    /** Fraction played, or 0 when the server reported nothing. */
    val progress: Float
        get() = when {
            durationMs > 0 -> (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }

    val subtitle: String
        get() = buildList {
            year?.let { add(it.toString()) }
            rating?.takeIf { it > 0 }?.let { add(String.format("%.1f", it)) }
            if (size > 0) add("${size / 1024 / 1024} MB")
            if (durationMs > 0) add(timecode(durationMs))
        }.joinToString(" · ")

    companion object {
        fun timecode(ms: Long): String {
            if (ms <= 0) return ""
            val total = ms / 1000
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
            else "${m} 分 ${String.format("%02d", s)} 秒"
        }
    }
}

data class UrlListItem(val url: String, val name: String, val userAgent: String? = null)

data class HistoryEntry(
    val url: String,
    val name: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    /**
     * Artwork, when whatever started the playback knew an address for it. The home
     * shelf draws this; a bare URL carries nothing a still could be pulled from,
     * which is why every remote entry used to land on the same grey tile.
     */
    val imageUrl: String? = null
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

data class PlayRequest(
    val url: String,
    val name: String,
    val subtitleUrl: String? = null,
    val userAgent: String? = null,
    val positionMs: Long = 0L,
    val headers: Map<String, String> = emptyMap(),
    val saveToUrlList: Boolean = false,
    /** Playable siblings of the current folder, used for continuous playback. */
    val queue: List<Pair<String, String>> = emptyList(),
    /**
     * "网盘限速提示". A presigned object-storage URL says nothing about where it came
     * from, so the source has to travel with the request.
     */
    val isCloudDrive: Boolean = false,
    /** Artwork address, so the resume shelf keeps the poster the browser showed. */
    val imageUrl: String? = null,
    /**
     * Total length as the server reported it. Media3 reads the container itself, but
     * over a stream that can come back unknown, and a player that cannot say how long
     * a film is cannot draw a scrubber for it either. This seeds the bar until — and
     * only until — the player reports a figure of its own.
     */
    val durationMs: Long = 0L
)
