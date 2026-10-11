package com.senplay.deeplink

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.senplay.core.PlayRequest
import com.senplay.core.ServerConfig
import java.util.UUID

/**
 * Parses the URL-scheme contract published with the analysed app so external
 * callers written for it keep working against this implementation.
 */
sealed interface Route {
    data class Play(val request: PlayRequest) : Route
    data class Download(val url: String, val name: String, val userAgent: String?) : Route
    data class ImportServer(val server: ServerConfig) : Route
    data class ImportIcon(val iconset: String) : Route
    data object None : Route
}

object SchemeRouter {

    private val supportedSchemes = setOf("senplay", "senplayer")

    fun parse(uri: Uri?): Route {
        if (uri == null) return Route.None
        val scheme = uri.scheme?.lowercase() ?: return Route.None
        if (scheme !in supportedSchemes) return Route.None

        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path?.trim('/')?.lowercase().orEmpty()
        val q = { key: String -> uri.getQueryParameter(key) }

        return when {
            host == "x-callback-url" && path == "play" -> {
                val url = q("url") ?: return Route.None
                Route.Play(
                    PlayRequest(
                        url = url,
                        name = q("name")?.takeIf { it.isNotBlank() }
                            ?: url.substringAfterLast('/').takeIf { it.isNotBlank() } ?: "Video",
                        subtitleUrl = q("sub"),
                        userAgent = q("User-Agent"),
                        positionMs = (q("position")?.toLongOrNull() ?: 0L) * 1000L,
                        saveToUrlList = uri.queryParameterNames.contains("saveURL")
                    )
                )
            }

            host == "x-callback-url" && path == "download" -> {
                val url = q("url") ?: return Route.None
                Route.Download(url, q("name") ?: url.substringAfterLast('/'), q("User-Agent"))
            }

            host == "importserver" || path == "importserver" -> {
                val address = q("address") ?: return Route.None
                val type = q("type")?.lowercase() ?: "webdav"
                val fallbacks = mutableListOf<List<String>>()
                var index = 1
                while (index <= 8) {
                    val fa = q("address$index")
                    if (fa.isNullOrBlank()) break
                    fallbacks += listOf(q("address${index}name").orEmpty(), fa)
                    index++
                }
                Route.ImportServer(
                    ServerConfig(
                        id = UUID.randomUUID().toString(),
                        type = type,
                        name = q("name") ?: type,
                        address = address,
                        username = q("username").orEmpty(),
                        password = q("password").orEmpty(),
                        note = q("note").orEmpty(),
                        fallbackAddresses = fallbacks
                    )
                )
            }

            host == "importicon" || path == "importicon" -> {
                val iconset = q("iconset") ?: return Route.None
                Route.ImportIcon(iconset)
            }

            else -> Route.None
        }
    }

    /** x-callback-url reply: returns the position the player closed at. */
    fun dispatchCallback(context: Context, source: Uri, lastPlayedUrl: String, positionSeconds: Long) {
        val base = source.getQueryParameter("x-success") ?: return
        val separator = if (base.contains('?')) '&' else '?'
        val target = Uri.parse(
            base + separator +
                "lastPlayedUrl=" + Uri.encode(lastPlayedUrl) +
                "&position=" + positionSeconds
        )
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
