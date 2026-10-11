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
    val imageUrl: String? = null
)

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
    val saveToUrlList: Boolean = false
)
