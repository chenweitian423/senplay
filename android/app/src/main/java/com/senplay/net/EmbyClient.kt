package com.senplay.net

import com.senplay.core.Http
import com.senplay.core.MediaEntry
import com.senplay.core.ServerConfig
import com.senplay.core.Urls
import com.senplay.core.io
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Emby / Jellyfin / 飞牛 compatible client using the documented
 * X-Emby-Authorization header plus /Users/AuthenticateByName and /Items.
 */
class EmbyClient(private val server: ServerConfig) {

    var accessToken: String = ""
        private set

    var userId: String = ""
        private set

    private val clientId = "senplay-android"

    private val authHeader: String
        get() {
            val base = "MediaBrowser Client=\"SenPlay\", Device=\"Android\", DeviceId=\"$clientId\", Version=\"1.0.0\""
            return if (accessToken.isBlank()) base else base + ", Token=\"" + accessToken + "\""
        }

    fun authenticate(): Boolean = io {
        val body = JSONObject()
            .put("Username", server.username)
            .put("Pw", server.password)
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(Urls.join(server.address, "/Users/AuthenticateByName"))
            .post(body)
            .header("X-Emby-Authorization", authHeader)
            .header("Content-Type", "application/json")
            .build()

        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Emby 认证失败 ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            accessToken = json.optString("AccessToken")
            userId = json.optJSONObject("User")?.optString("Id").orEmpty()
            accessToken.isNotBlank()
        }
    }

    fun views(): List<MediaEntry> = items(null, "CollectionFolder,Folder,UserView")

    fun items(parentId: String?, includeTypes: String = "Folder,Movie,Episode,Series,Video"): List<MediaEntry> = io {
        val builder = Urls.join(server.address, "/Users/$userId/Items")
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("IncludeItemTypes", includeTypes)
            .addQueryParameter("Recursive", "false")
            .addQueryParameter("Fields", "Path,MediaSources,RunTimeTicks")
            .addQueryParameter("SortBy", "SortName")
            .addQueryParameter("api_key", accessToken)
        if (!parentId.isNullOrBlank()) builder.addQueryParameter("ParentId", parentId)

        val request = Request.Builder()
            .url(builder.build())
            .header("X-Emby-Authorization", authHeader)
            .build()

        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Emby 列表失败 ${response.code}")
            val items = JSONObject(response.body?.string().orEmpty()).optJSONArray("Items")
                ?: return@io emptyList()
            (0 until items.length()).mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("Id")
                if (id.isBlank()) return@mapNotNull null
                val type = item.optString("Type")
                val folderLike = type == "Folder" || type == "Series" ||
                    type == "CollectionFolder" || type == "UserView" || type == "Season"
                MediaEntry(
                    name = item.optString("Name", id),
                    url = id,
                    isDirectory = folderLike,
                    entryId = id,
                    durationTicks = item.optLong("RunTimeTicks"),
                    played = item.optJSONObject("UserData")?.optBoolean("Played") ?: false,
                    imageUrl = Urls.join(
                        server.address,
                        "/Items/$id/Images/Primary?maxHeight=360&api_key=$accessToken"
                    )
                )
            }
        }
    }

    fun streamUrl(entry: MediaEntry): String =
        Urls.join(server.address, "/Videos/${entry.entryId}/stream?static=true&api_key=$accessToken")

    fun mediaHeaders(): Map<String, String> = mapOf("X-Emby-Authorization" to authHeader)
}
