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
    /**
     * Emby renders timestamps as ISO-8601 without a timezone suffix. Two patterns
     * because newer builds include fractional seconds and older ones do not.
     */
    /**
     * Emby lists everyone credited; the app only has room for a few names, and the
     * order it returns is the billing order.
     */
    private fun castOf(item: org.json.JSONObject, limit: Int = 4): List<String> {
        val people = item.optJSONArray("People") ?: return emptyList()
        val names = mutableListOf<String>()
        for (index in 0 until people.length()) {
            val person = people.optJSONObject(index) ?: continue
            if (person.optString("Type") != "Actor") continue
            person.optString("Name").takeIf { it.isNotBlank() }?.let { names += it }
            if (names.size >= limit) break
        }
        return names
    }

    private fun dateMillis(raw: String): Long {
        if (raw.isBlank()) return 0L
        for (pattern in listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )) {
            runCatching {
                return java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                    .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                    .parse(raw)!!.time
            }
        }
        return 0L
    }


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
            .addQueryParameter("Fields", "Path,MediaSources,RunTimeTicks,Overview,ProductionYear,CommunityRating,UserData,DateModified,People")
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
            .addQueryParameter("Fields", "Path,MediaSources,RunTimeTicks,UserData,DateModified")
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
                    unplayedCount = userData?.optInt("UnplayedItemCount") ?: 0,
                    episodeNumber = item.optInt("IndexNumber"),
                    cast = castOf(item),
                    modifiedAt = dateMillis(item.optString("DateModified")),
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
