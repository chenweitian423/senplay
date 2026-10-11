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

    suspend fun authenticate(): Boolean = io {
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

    enum class SortBy(val wire: String, val title: String) {
        NAME("SortName", "名称"),
        DATE_ADDED("DateCreated", "加入时间"),
        YEAR("ProductionYear", "年份"),
        RATING("CommunityRating", "评分"),
        RUNTIME("Runtime", "时长")
    }

    suspend fun views(): List<MediaEntry> = items(null, "CollectionFolder,Folder,UserView")

    suspend fun items(
        parentId: String?,
        includeTypes: String = "Folder,Movie,Episode,Series,Video",
        sortBy: SortBy = SortBy.NAME,
        ascending: Boolean = true,
        recursive: Boolean = false,
        searchTerm: String? = null,
        limit: Int? = null
    ): List<MediaEntry> = io {
        val builder = Urls.join(server.address, "/Users/$userId/Items")
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("IncludeItemTypes", includeTypes)
            .addQueryParameter("Recursive", if (recursive) "true" else "false")
            .addQueryParameter("Fields", "Path,MediaSources,RunTimeTicks,Overview,ProductionYear,CommunityRating,UserData")
            .addQueryParameter("SortBy", sortBy.wire)
            .addQueryParameter("SortOrder", if (ascending) "Ascending" else "Descending")
            .addQueryParameter("ImageTypeLimit", "1")
            .addQueryParameter("EnableImageTypes", "Primary,Backdrop,Thumb")
            .addQueryParameter("api_key", accessToken)
        if (!parentId.isNullOrBlank()) builder.addQueryParameter("ParentId", parentId)
        if (!searchTerm.isNullOrBlank()) builder.addQueryParameter("SearchTerm", searchTerm)
        limit?.let { builder.addQueryParameter("Limit", it.toString()) }

        fetchItems(builder.build())
    }

    /** "继续观看": whatever the server reports as partially played. */
    /** Box sets: the server-side groupings that behave as collections. */
    suspend fun collections(parentId: String? = null): List<MediaEntry> =
        items(parentId, includeTypes = "BoxSet", sortBy = SortBy.NAME, ascending = true, recursive = true)

    suspend fun resume(limit: Int = 30): List<MediaEntry> = io {
        val url = Urls.join(server.address, "/Users/$userId/Items/Resume")
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("Recursive", "true")
            .addQueryParameter("MediaTypes", "Video")
            .addQueryParameter("Limit", limit.toString())
            .addQueryParameter("Fields", "Path,MediaSources,RunTimeTicks,UserData")
            .addQueryParameter("api_key", accessToken)
            .build()
        fetchItems(url)
    }

    suspend fun latest(limit: Int = 30): List<MediaEntry> =
        items(null, "Movie,Episode,Video", SortBy.DATE_ADDED, ascending = false, recursive = true, limit = limit)

    private fun fetchItems(url: okhttp3.HttpUrl): List<MediaEntry> {
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Authorization", authHeader)
            .build()

        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Emby 列表失败 ${response.code}")
            val items = JSONObject(response.body?.string().orEmpty()).optJSONArray("Items")
                ?: return emptyList()
            return (0 until items.length()).mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("Id")
                if (id.isBlank()) return@mapNotNull null
                val type = item.optString("Type")
                val folderLike = type == "Folder" || type == "Series" || type == "Season" ||
                    type == "CollectionFolder" || type == "UserView" || type == "BoxSet"
                val userData = item.optJSONObject("UserData")
                MediaEntry(
                    name = item.optString("Name", id),
                    url = id,
                    isDirectory = folderLike,
                    entryId = id,
                    durationTicks = item.optLong("RunTimeTicks"),
                    played = userData?.optBoolean("Played") ?: false,
                    positionMs = (userData?.optLong("PlaybackPositionTicks") ?: 0L) / 10_000L,
                    overview = item.optString("Overview").ifBlank { null },
                    year = item.optInt("ProductionYear").takeIf { it > 0 },
                    rating = item.optDouble("CommunityRating").takeIf { it > 0 },
                    imageUrl = Urls.join(
                        server.address,
                        "/Items/$id/Images/Primary?maxHeight=400&api_key=$accessToken"
                    )
                )
            }
        }
    }

    fun streamUrl(entry: MediaEntry): String =
        Urls.join(server.address, "/Videos/${entry.entryId}/stream?static=true&api_key=$accessToken")

    fun mediaHeaders(): Map<String, String> = mapOf("X-Emby-Authorization" to authHeader)
}
