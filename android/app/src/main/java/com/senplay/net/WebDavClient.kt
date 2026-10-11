package com.senplay.net

import android.util.Xml
import com.senplay.core.Http
import com.senplay.core.LibraryStore
import com.senplay.core.MediaEntry
import com.senplay.core.ServerConfig
import com.senplay.core.Urls
import com.senplay.core.io
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/** Minimal WebDAV client: depth-1 PROPFIND listing, enough to browse and stream. */
object WebDavClient {

    private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:displayname/>
    <d:getcontentlength/>
    <d:getcontenttype/>
    <d:resourcetype/>
  </d:prop>
</d:propfind>"""

    suspend fun list(server: ServerConfig, path: String): List<MediaEntry> = io {
        val url = Urls.join(server.address, path)
        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", PROPFIND_BODY.toRequestBody("application/xml".toMediaType()))
            .header("Depth", "1")
            .header("Authorization", Http.basic(server.username, server.password))
            .header("User-Agent", LibraryStore.DEFAULT_UA)
            .build()

        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("WebDAV ${response.code} ${response.message}")
            }
            parseMultiStatus(response.body?.string().orEmpty(), url)
        }
    }

    private fun parseMultiStatus(xml: String, requestUrl: String): List<MediaEntry> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        val entries = mutableListOf<MediaEntry>()
        var href: String? = null
        var displayName: String? = null
        var contentLength = 0L
        var isCollection = false
        var inResponse = false
        var inPropStat = false
        var lastElement = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "response" -> {
                            inResponse = true
                            href = null
                            displayName = null
                            contentLength = 0L
                            isCollection = false
                        }
                        "propstat" -> inPropStat = true
                        "collection" -> isCollection = true
                    }
                    lastElement = name
                }
                XmlPullParser.TEXT -> {
                    if (inResponse && inPropStat) {
                        when (lastElement) {
                            "href" -> href = parser.text?.trim()
                            "displayname" -> displayName = parser.text?.trim()
                            "getcontentlength" -> contentLength = parser.text?.trim()?.toLongOrNull() ?: 0L
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (name) {
                        "propstat" -> inPropStat = false
                        "response" -> {
                            inResponse = false
                            val raw = href
                            if (raw != null) {
                                val decoded = Urls.decode(raw)
                                val self = decoded.trimEnd('/') == requestUrl.trimEnd('/')
                                if (!self) {
                                    val label = displayName?.takeIf { it.isNotBlank() }
                                        ?: decoded.trimEnd('/').substringAfterLast('/')
                                    val absolute =
                                        if (decoded.startsWith("http")) decoded
                                        else Urls.join(Urls.origin(requestUrl), decoded)
                                    entries += MediaEntry(
                                        name = label,
                                        url = absolute,
                                        isDirectory = isCollection,
                                        size = contentLength
                                    )
                                }
                            }
                        }
                    }
                    lastElement = ""
                }
            }
            event = parser.next()
        }
        return entries.sortedWith(
            compareByDescending<MediaEntry> { it.isDirectory }.thenBy { it.name.lowercase() }
        )
    }
}
