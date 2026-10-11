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
    val updatedAt: Long
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
    val queue: List<Pair<String, String>> = emptyList()
)
