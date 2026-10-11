package com.senplay.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

enum class DanmakuMode(val wire: Int) {
    SCROLL(1),
    TOP(4),
    BOTTOM(5),
    REVERSE(6);

    companion object {
        fun of(value: Int): DanmakuMode = values().firstOrNull { it.wire == value } ?: SCROLL
    }
}

data class DanmakuItem(
    val time: Double,
    val mode: DanmakuMode,
    val color: Int,
    val text: String
)

data class DanmakuEndpoint(
    val name: String,
    val url: String
)

/**
 * dandanplay-compatible protocol: search an episode by keyword, then pull its
 * comment list. Only the two documented endpoints are used, so any service that
 * implements the same shape works. Endpoints are user-supplied; no address is
 * baked into the build.
 */
object DanmakuLoader {

    suspend fun fetch(keyword: String, base: String): List<DanmakuItem> = withContext(Dispatchers.IO) {
        if (base.isBlank() || keyword.isBlank()) return@withContext emptyList()
        val episodeId = searchEpisode(keyword.trim(), base) ?: return@withContext emptyList()
        fetchComments(episodeId, base)
    }

    private fun open(url: String): String {
        val connection = (URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "SenPlay/1.1 (Android)")
        }
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun searchEpisode(keyword: String, base: String): Int? = runCatching {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val json = JSONObject(open("$base/api/v2/search/episodes?anime=$encoded"))
        val animes = json.optJSONArray("animes") ?: return null
        for (index in 0 until animes.length()) {
            val episodes = animes.optJSONObject(index)?.optJSONArray("episodes") ?: continue
            if (episodes.length() > 0) {
                val id = episodes.optJSONObject(0)?.optInt("episodeId") ?: 0
                if (id > 0) return id
            }
        }
        null
    }.getOrNull()

    private fun fetchComments(episodeId: Int, base: String): List<DanmakuItem> = runCatching {
        val json = JSONObject(open("$base/api/v2/comment/$episodeId?withRelated=true"))
        val comments = json.optJSONArray("comments") ?: return emptyList()
        (0 until comments.length()).mapNotNull { index ->
            val comment = comments.optJSONObject(index) ?: return@mapNotNull null
            val p = comment.optString("p")
            val message = comment.optString("m")
            if (p.isBlank() || message.isBlank()) return@mapNotNull null
            val parts = p.split(",")
            val time = parts.firstOrNull()?.toDoubleOrNull() ?: return@mapNotNull null
            DanmakuItem(
                time = time,
                mode = DanmakuMode.of(parts.getOrNull(1)?.toIntOrNull() ?: 1),
                color = parts.getOrNull(2)?.toIntOrNull() ?: 0xFFFFFF,
                text = message
            )
        }.sortedBy { it.time }
    }.getOrDefault(emptyList())
}

/**
 * Endpoints live in preferences under "pref.danmaku.endpoint.N"; the list is capped so a
 * stray key cannot grow the surface unbounded. Empty entries are skipped, which is
 * what keeps a fresh install from pointing anywhere.
 */
fun danmakuEndpoints(store: LibraryStore): List<String> =
    (1..5).mapNotNull { index ->
        store.prefString("pref.danmaku.endpoint.$index", "").takeIf { it.isNotBlank() }
    }
