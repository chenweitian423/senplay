package com.senplay.net

import com.senplay.core.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory

data class PlexLibrary(val id: String, val title: String, val type: String)

data class PlexItem(
    val id: String,
    val title: String,
    val year: Int?,
    val durationMs: Long,
    val thumbPath: String?,
    val partPath: String?,
    val partSize: Long,
    val viewOffsetMs: Long,
    val isPlayed: Boolean
)

/**
 * Plex speaks XML over HTTP with a token header rather than JSON, so it gets its own
 * client. The token rides in the password field of the server entry, which keeps one
 * credential form for every server type.
 */
class PlexClient(private val server: ServerConfig) {

    private val token: String =
        if (server.password.isBlank()) server.username else server.password

    private val base: String
        get() {
            var address = server.address
            if (!address.lowercase().startsWith("http")) address = "http://$address"
            return address.trimEnd('/')
        }

    private fun fetch(path: String): String {
        val separator = if (path.contains("?")) "&" else "?"
        val url = URL("$base$path$separator" + "X-Plex-Token=" + URLEncoder.encode(token, "UTF-8"))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/xml")
            setRequestProperty("X-Plex-Product", "SenPlay")
            setRequestProperty("X-Plex-Client-Identifier", "senplay-android")
        }
        return try {
            if (connection.responseCode !in 200..299) {
                error("Plex 返回 ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun elements(xml: String, tag: String): List<Element> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        val document = factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        val nodes = document.getElementsByTagName(tag)
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    suspend fun libraries(): List<PlexLibrary> = withContext(Dispatchers.IO) {
        elements(fetch("/library/sections"), "Directory").mapNotNull { element ->
            val key = element.getAttribute("key")
            val title = element.getAttribute("title")
            if (key.isBlank() || title.isBlank()) null
            else PlexLibrary(key, title, element.getAttribute("type"))
        }
    }

    suspend fun items(section: String, search: String? = null): List<PlexItem> = withContext(Dispatchers.IO) {
        val path = if (search.isNullOrBlank()) {
            "/library/sections/$section/all"
        } else {
            "/library/sections/$section/search?query=" + URLEncoder.encode(search, "UTF-8")
        }
        toItems(fetch(path))
    }

    /** Continue watching, straight from the server's own view. */
    suspend fun onDeck(): List<PlexItem> = withContext(Dispatchers.IO) {
        toItems(fetch("/library/onDeck"))
    }

    /** Plex collections live under a section rather than globally. */
    suspend fun collections(section: String): List<PlexLibrary> = withContext(Dispatchers.IO) {
        elements(fetch("/library/sections/$section/collections"), "Directory").mapNotNull { element ->
            val key = element.getAttribute("ratingKey").ifBlank { element.getAttribute("key") }
            val title = element.getAttribute("title")
            if (key.isBlank() || title.isBlank()) null else PlexLibrary(key, title, "collection")
        }
    }

    /** Items inside one collection, by its rating key. */
    suspend fun itemsInCollection(key: String): List<PlexItem> = withContext(Dispatchers.IO) {
        toItems(fetch("/library/collections/$key/children"))
    }

    suspend fun recentlyAdded(): List<PlexItem> = withContext(Dispatchers.IO) {
        toItems(fetch("/library/recentlyAdded"))
    }

    private fun toItems(xml: String): List<PlexItem> {
        val videos = elements(xml, "Video")
        val directories = elements(xml, "Directory")
        return (videos + directories).map { element ->
            val media = element.getElementsByTagName("Media").item(0) as? Element
            val part = media?.getElementsByTagName("Part")?.item(0) as? Element
            PlexItem(
                id = element.getAttribute("ratingKey").ifBlank { element.getAttribute("key") },
                title = element.getAttribute("title").ifBlank { "未命名" },
                year = element.getAttribute("year").toIntOrNull(),
                durationMs = element.getAttribute("duration").toLongOrNull() ?: 0L,
                thumbPath = element.getAttribute("thumb").ifBlank { null },
                partPath = part?.getAttribute("key")?.ifBlank { null },
                partSize = part?.getAttribute("size")?.toLongOrNull() ?: 0L,
                viewOffsetMs = element.getAttribute("viewOffset").toLongOrNull() ?: 0L,
                isPlayed = (element.getAttribute("viewCount").toIntOrNull() ?: 0) > 0
            )
        }
    }

    /** Direct file URL, bypassing the transcoder so the player gets the original. */
    fun streamUrl(item: PlexItem): String? {
        val part = item.partPath ?: return null
        val separator = if (part.contains("?")) "&" else "?"
        return "$base$part$separator" + "X-Plex-Token=" + URLEncoder.encode(token, "UTF-8")
    }

    fun artworkUrl(item: PlexItem): String? {
        val thumb = item.thumbPath ?: return null
        return "$base$thumb?X-Plex-Token=" + URLEncoder.encode(token, "UTF-8")
    }

    fun mediaHeaders(): Map<String, String> = mapOf("X-Plex-Token" to token)
}
