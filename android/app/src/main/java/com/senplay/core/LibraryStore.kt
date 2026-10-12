package com.senplay.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single persistence facade. SharedPreferences + JSON keeps the project free of any
 * annotation-processor dependency so CI stays green in one shot.
 */
class LibraryStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("senplay", Context.MODE_PRIVATE)

    fun servers(): List<ServerConfig> =
        readArray(KEY_SERVERS).mapNotNull { o ->
            val id = o.optString("id")
            if (id.isBlank()) null else ServerConfig(
                id = id,
                type = o.optString("type", "webdav"),
                name = o.optString("name"),
                address = o.optString("address"),
                username = o.optString("username"),
                password = o.optString("password"),
                note = o.optString("note")
            )
        }

    fun saveServer(server: ServerConfig) {
        val all = servers().filterNot { it.id == server.id } + server
        writeArray(KEY_SERVERS, all.map { it.toJson() })
    }

    fun removeServer(id: String) {
        writeArray(KEY_SERVERS, servers().filterNot { it.id == id }.map { it.toJson() })
    }

    fun urlList(): List<UrlListItem> = readArray(KEY_URLS).mapNotNull { o ->
        val url = o.optString("url")
        if (url.isBlank()) null
        else UrlListItem(url, o.optString("name", url), o.optString("userAgent").ifBlank { null })
    }

    fun addUrl(item: UrlListItem) {
        val all = urlList().filterNot { it.url == item.url } + item
        writeArray(KEY_URLS, all.map { it.toJson() })
    }

    fun removeUrl(url: String) {
        writeArray(KEY_URLS, urlList().filterNot { it.url == url }.map { it.toJson() })
    }

    fun history(): List<HistoryEntry> = readArray(KEY_HISTORY).mapNotNull { o ->
        val url = o.optString("url")
        if (url.isBlank()) null
        else HistoryEntry(
            url,
            o.optString("name", url),
            o.optLong("position"),
            o.optLong("duration"),
            o.optLong("updatedAt"),
            o.optString("imageUrl").ifBlank { null }
        )
    }.sortedByDescending { it.updatedAt }

    fun rememberProgress(url: String, name: String, positionMs: Long, durationMs: Long,
                         imageUrl: String? = null) {
        // The artwork is sticky: a later visit that arrives carrying none — play resumed
        // from the history list, say — keeps what the browser recorded rather than
        // blanking the tile the shelf already drew.
        val previous = history()
        val kept = imageUrl ?: previous.firstOrNull { it.url == url }?.imageUrl
        val entry = HistoryEntry(url, name, positionMs, durationMs, System.currentTimeMillis(), kept)
        val all = previous.filterNot { it.url == url } + entry
        writeArray(KEY_HISTORY, all.sortedByDescending { it.updatedAt }.take(300).map { it.toJson() })
    }

    fun savedPosition(url: String): Long = history().firstOrNull { it.url == url }?.positionMs ?: 0L

    fun removeHistory(url: String) {
        writeArray(KEY_HISTORY, history().filterNot { it.url == url }.map { it.toJson() })
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    var rememberProgressEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER, true)
        set(value) {
            prefs.edit().putBoolean(KEY_REMEMBER, value).apply()
        }

    var defaultUserAgent: String
        get() = prefs.getString(KEY_UA, DEFAULT_UA) ?: DEFAULT_UA
        set(value) {
            prefs.edit().putString(KEY_UA, value).apply()
        }

    // ---- track memory ------------------------------------------------------

    /**
     * "记住所选字幕/音轨": which track was picked for a given file, so the next visit
     * starts on it. One JSON object rather than a storage key per file — the file is
     * a URL, and a URL makes a poor key. The two switches are separate, so a viewer
     * can keep the subtitle choice without pinning the audio.
     */
    fun rememberedTrack(url: String, kind: String): String? = runCatching {
        JSONObject(value(KEY_TRACKS) ?: "{}")
            .optJSONObject(url)
            ?.optString(kind)
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun rememberTrack(url: String, kind: String, track: String) {
        val root = runCatching { JSONObject(value(KEY_TRACKS) ?: "{}") }
            .getOrDefault(JSONObject())
        val entry = root.optJSONObject(url) ?: JSONObject()
        entry.put(kind, track)
        root.put(url, entry)
        setValue(KEY_TRACKS, root.toString())
    }

    // ---- preferences -------------------------------------------------------
    // Keys are namespaced with "pref." so clearPreferences() can wipe playback
    // preferences without touching servers, URL list or history.

    fun prefBool(key: String, def: Boolean): Boolean = prefs.getBoolean(PREF_PREFIX + key, def)
    fun setPrefBool(key: String, value: Boolean) = prefs.edit().putBoolean(PREF_PREFIX + key, value).apply()

    fun prefInt(key: String, def: Int): Int = prefs.getInt(PREF_PREFIX + key, def)
    fun setPrefInt(key: String, value: Int) = prefs.edit().putInt(PREF_PREFIX + key, value).apply()

    fun prefFloat(key: String, def: Float): Float = prefs.getFloat(PREF_PREFIX + key, def)
    fun setPrefFloat(key: String, value: Float) = prefs.edit().putFloat(PREF_PREFIX + key, value).apply()

    fun prefString(key: String, def: String): String = prefs.getString(PREF_PREFIX + key, def) ?: def
    fun setPrefString(key: String, value: String) = prefs.edit().putString(PREF_PREFIX + key, value).apply()

    /**
     * Raw storage, outside the "pref." namespace.
     *
     * Playlists and channel lists are user data, not playback preferences: clearing
     * those must not take the user's IPTV sources with them.
     */
    fun value(key: String): String? = prefs.getString(key, null)
    fun setValue(key: String, value: String) = prefs.edit().putString(key, value).apply()

    fun clearPreferences() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(PREF_PREFIX) }.forEach { editor.remove(it) }
        editor.apply()
    }

    private fun readArray(key: String): List<JSONObject> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        }.getOrDefault(emptyList())
    }

    private fun writeArray(key: String, items: List<JSONObject>) {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun ServerConfig.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("name", name)
        put("address", address)
        put("username", username)
        put("password", password)
        put("note", note)
    }

    private fun UrlListItem.toJson(): JSONObject = JSONObject().apply {
        put("url", url)
        put("name", name)
        userAgent?.let { put("userAgent", it) }
    }

    companion object {
        private const val KEY_SERVERS = "servers"
        private const val KEY_URLS = "url_list"
        private const val KEY_HISTORY = "history"
        private const val KEY_REMEMBER = "remember_progress"
        private const val KEY_UA = "default_user_agent"
        private const val KEY_TRACKS = "track_memory"
        private const val PREF_PREFIX = "pref."
        const val DEFAULT_UA = "SenPlay/1.0 (Android)"

        @Volatile
        private var instance: LibraryStore? = null

        fun get(context: Context): LibraryStore =
            instance ?: synchronized(this) {
                instance ?: LibraryStore(context).also { instance = it }
            }
    }
}

/**
 * One history row as it is stored. Shared by every writer so that deleting an
 * unrelated entry cannot quietly drop a field the others keep — which is exactly what
 * would happen to the artwork if each site kept building its own JSONObject.
 */
private fun HistoryEntry.toJson(): JSONObject = JSONObject().apply {
    put("url", url)
    put("name", name)
    put("position", positionMs)
    put("duration", durationMs)
    put("updatedAt", updatedAt)
    imageUrl?.let { put("imageUrl", it) }
}
