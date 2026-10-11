package com.senplay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.senplay.core.LibraryStore
import com.senplay.core.PlayRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Channel(
    val name: String,
    val url: String,
    val group: String,
    val logo: String? = null,
    val tvgId: String? = null
)

/**
 * M3U / M3U8 playlist parser. Handles the extended attributes IPTV lists actually
 * ship (`tvg-id`, `tvg-logo`, `group-title`) and tolerates CRLF, BOM and blank
 * lines. Anything it cannot classify is skipped rather than guessed at.
 */
object M3UParser {
    fun parse(text: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        var pendingName: String? = null
        var pendingGroup = "未分组"
        var pendingLogo: String? = null
        var pendingTvgId: String? = null

        text.split('\n').forEach { raw ->
            val line = raw.trim().removePrefix("\uFEFF")
            if (line.isEmpty()) return@forEach

            if (line.startsWith("#EXTINF:")) {
                val payload = line.removePrefix("#EXTINF:")
                val comma = payload.lastIndexOf(',')
                if (comma >= 0) {
                    pendingName = payload.substring(comma + 1).trim()
                    val attrs = payload.substring(0, comma)
                    pendingGroup = attribute("group-title", attrs) ?: "未分组"
                    pendingLogo = attribute("tvg-logo", attrs)
                    pendingTvgId = attribute("tvg-id", attrs)
                } else {
                    pendingName = null
                }
                return@forEach
            }

            if (line.startsWith("#")) return@forEach

            if (line.contains("://")) {
                pendingName?.let { name ->
                    channels += Channel(name, line, pendingGroup, pendingLogo, pendingTvgId)
                }
                pendingName = null
                pendingGroup = "未分组"
                pendingLogo = null
                pendingTvgId = null
            }
        }
        return channels
    }

    private fun attribute(key: String, text: String): String? {
        val marker = "$key=\""
        val start = text.indexOf(marker)
        if (start < 0) return null
        val from = start + marker.length
        val end = text.indexOf('"', from)
        if (end < 0) return null
        return text.substring(from, end).trim().ifEmpty { null }
    }
}

/** Sources and the channel cache live in SharedPreferences through LibraryStore. */
private const val P_IPTV_SOURCES = "iptv.sources"
private const val P_IPTV_CHANNELS = "iptv.channels"

private fun loadSources(store: LibraryStore): List<Pair<String, String>> = runCatching {
    val raw = store.value(P_IPTV_SOURCES).orEmpty()
    if (raw.isBlank()) return@runCatching emptyList()
    val array = JSONArray(raw)
    (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        item.optString("name") to item.optString("url")
    }
}.getOrDefault(emptyList())

private fun saveSources(store: LibraryStore, sources: List<Pair<String, String>>) {
    val array = JSONArray()
    sources.forEach { (name, url) ->
        array.put(JSONObject().apply {
            put("name", name)
            put("url", url)
        })
    }
    store.setValue(P_IPTV_SOURCES, array.toString())
}

private fun loadChannels(store: LibraryStore): List<Channel> = runCatching {
    val raw = store.value(P_IPTV_CHANNELS).orEmpty()
    if (raw.isBlank()) return@runCatching emptyList()
    val array = JSONArray(raw)
    (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        Channel(
            name = item.optString("name"),
            url = item.optString("url"),
            group = item.optString("group", "未分组"),
            logo = item.optString("logo").ifBlank { null },
            tvgId = item.optString("tvgId").ifBlank { null }
        )
    }
}.getOrDefault(emptyList())

private fun saveChannels(store: LibraryStore, channels: List<Channel>) {
    val array = JSONArray()
    channels.forEach { channel ->
        array.put(JSONObject().apply {
            put("name", channel.name)
            put("url", channel.url)
            put("group", channel.group)
            channel.logo?.let { put("logo", it) }
            channel.tvgId?.let { put("tvgId", it) }
        })
    }
    store.setValue(P_IPTV_CHANNELS, array.toString())
}

private fun fetchPlaylist(url: String): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 20_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "SenPlay/1.1 (Android)")
    }
    return try {
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

@Composable
fun IPTVScreen(store: LibraryStore, onPlay: (PlayRequest) -> Unit) {
    var sources by remember { mutableStateOf(loadSources(store)) }
    var channels by remember { mutableStateOf(loadChannels(store)) }
    var search by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var openGroup by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        if (sources.isEmpty()) return
        refreshing = true
        scope.launch {
            val collected = mutableListOf<Channel>()
            var failure: String? = null
            withContext(Dispatchers.IO) {
                sources.forEach { (name, url) ->
                    runCatching { M3UParser.parse(fetchPlaylist(url)) }
                        .onSuccess { collected += it }
                        .onFailure { failure = "$name：${it.message}" }
                }
            }
            if (collected.isNotEmpty()) {
                channels = collected
                saveChannels(store, collected)
            }
            error = failure
            refreshing = false
        }
    }

    LaunchedEffect(Unit) {
        if (channels.isEmpty() && sources.isNotEmpty()) refresh()
    }

    val query = search.trim()
    val visible = if (query.isEmpty()) {
        channels
    } else {
        channels.filter {
            it.name.contains(query, ignoreCase = true) || it.group.contains(query, ignoreCase = true)
        }
    }
    val groups = visible.map { it.group }.distinct().sorted()

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("搜索频道") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { refresh() }, enabled = sources.isNotEmpty() && !refreshing) {
                    if (refreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                }
                IconButton(onClick = { showAdd = true }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "添加播放列表")
                }
            }
        }

        error?.let { message ->
            item {
                Text(
                    "错误：$message",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        if (sources.isEmpty()) {
            item {
                Text(
                    "添加一个 M3U/M3U8 地址来载入频道列表。列表会缓存在本机。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        if (sources.isNotEmpty()) {
            item {
                Text(
                    "播放列表（${sources.size}）",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                )
            }
            items(sources, key = { it.second }) { source ->
                ListItem(
                    headlineContent = { Text(source.first, maxLines = 1) },
                    supportingContent = { Text(source.second, maxLines = 1) },
                    trailingContent = {
                        IconButton(onClick = {
                            val next = sources.filterNot { it.second == source.second }
                            sources = next
                            saveSources(store, next)
                        }) { Icon(Icons.Filled.Delete, contentDescription = "删除") }
                    }
                )
            }
        }

        if (openGroup == null) {
            groups.forEach { group ->
                val inGroup = visible.filter { it.group == group }
                item(key = "header-$group") {
                    Text(
                        "$group（${inGroup.size}）",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 4.dp)
                    )
                }
                items(inGroup, key = { it.url + it.name }) { channel ->
                    ListItem(
                        headlineContent = { Text(channel.name, maxLines = 1) },
                        supportingContent = channel.tvgId?.let { id: String ->
                            @Composable { Text(id, maxLines = 1) } },
                        leadingContent = { Icon(Icons.Filled.Tv, contentDescription = null) },
                        modifier = Modifier.clickable {
                            onPlay(
                                PlayRequest(
                                    url = channel.url,
                                    name = channel.name,
                                    queue = inGroup.map { it.url to it.name }
                                )
                            )
                        }
                    )
                }
            }
        } else {
            val group = openGroup!!
            val inGroup = visible.filter { it.group == group }
            item {
                ListItem(
                    headlineContent = { Text(group, maxLines = 1) },
                    leadingContent = {
                        IconButton(onClick = { openGroup = null }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
            items(inGroup, key = { it.url + it.name }) { channel ->
                ListItem(
                    headlineContent = { Text(channel.name, maxLines = 1) },
                    supportingContent = channel.tvgId?.let { id: String ->
                            @Composable { Text(id, maxLines = 1) } },
                    leadingContent = { Icon(Icons.Filled.Tv, contentDescription = null) },
                    modifier = Modifier.clickable {
                        onPlay(
                            PlayRequest(
                                url = channel.url,
                                name = channel.name,
                                queue = inGroup.map { it.url to it.name }
                            )
                        )
                    }
                )
            }
        }

        item { Spacer(modifier = Modifier.padding(12.dp)) }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("添加播放列表") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("名称") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("M3U 地址") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val next = sources + (name.ifBlank { url } to url)
                        sources = next
                        saveSources(store, next)
                        showAdd = false
                        refresh()
                    },
                    enabled = url.isNotBlank()
                ) { Text("添加并刷新") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("取消") }
            }
        )
    }
}
