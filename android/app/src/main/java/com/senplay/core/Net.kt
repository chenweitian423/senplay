package com.senplay.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun basic(user: String, password: String): String = Credentials.basic(user, password)
}

suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

/** URL helpers shared by the WebDAV and Emby clients. */
object Urls {
    fun join(base: String, path: String): String {
        if (path.startsWith("http://", true) || path.startsWith("https://", true)) return path
        val trimmed = base.trimEnd('/')
        if (path.isEmpty()) return trimmed
        return trimmed + "/" + path.trimStart('/')
    }

    fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    fun decode(value: String): String = java.net.URLDecoder.decode(value, "UTF-8")

    fun origin(url: String): String {
        val start = url.indexOf("://")
        if (start < 0) return url
        val slash = url.indexOf('/', start + 3)
        return if (slash > 0) url.substring(0, slash) else url
    }
}
