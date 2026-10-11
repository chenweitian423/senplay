package com.senplay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.senplay.core.LibraryStore
import com.senplay.core.MediaEntry
import com.senplay.core.PlayRequest
import com.senplay.core.ServerConfig
import com.senplay.net.EmbyClient
import com.senplay.net.PlexClient
import com.senplay.net.PlexItem
import com.senplay.net.PlexLibrary
import com.senplay.net.SmbTarget
import com.senplay.net.WebDavClient
import com.senplay.net.listFtpDirectory
import com.senplay.net.listSftpDirectory
import com.senplay.net.listSmbDirectory
import kotlinx.coroutines.launch
import java.util.UUID

private val SERVER_TYPES =
    listOf("webdav", "emby", "jellyfin", "feiniu", "plex", "smb", "ftp", "sftp", "s3")

@Composable
fun ServersScreen(store: LibraryStore, onPlay: (PlayRequest) -> Unit) {
    var servers by remember { mutableStateOf(store.servers()) }
    var showAdd by remember { mutableStateOf(false) }
    var showDiscovery by remember { mutableStateOf(false) }
    var browsing by remember { mutableStateOf<ServerConfig?>(null) }

    val current = browsing
    if (current == null) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("媒体服务器", style = MaterialTheme.typography.titleMedium)
                Row {
                    IconButton(onClick = { showDiscovery = true }) {
                        Icon(Icons.Filled.Wifi, contentDescription = "扫描局域网")
                    }
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "添加服务器")
                    }
                }
            }

            if (servers.isEmpty()) {
                Text(
                    "支持 WebDAV、Emby、Jellyfin、飞牛 fnOS。也可以用 senplay://importserver?type=webdav&address=... 一步导入，或直接扫描局域网。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(servers, key = { it.id }) { server ->
                    ListItem(
                        headlineContent = { Text(server.name.ifBlank { server.address }) },
                        supportingContent = { Text(server.type.uppercase() + " · " + server.address, maxLines = 1) },
                        trailingContent = {
                            IconButton(onClick = {
                                store.removeServer(server.id)
                                servers = store.servers()
                            }) { Icon(Icons.Filled.Delete, contentDescription = "删除") }
                        },
                        modifier = Modifier.clickable { browsing = server }
                    )
                }
            }
        }
    } else {
        ServerBrowserScreen(
            server = current,
            onBack = { browsing = null },
            onPlay = onPlay
        )
    }

    if (showDiscovery) {
        DiscoveryDialog(
            store = store,
            onDismiss = {
                showDiscovery = false
                servers = store.servers()
            }
        )
    }

    if (showAdd) {
        AddServerDialog(
            onDismiss = { showAdd = false },
            onSave = { server ->
                store.saveServer(server)
                servers = store.servers()
                showAdd = false
            }
        )
    }
}

@Composable
private fun ServerBrowserScreen(
    server: ServerConfig,
    onBack: () -> Unit,
    onPlay: (PlayRequest) -> Unit
) {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<MediaEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var path by remember { mutableStateOf("") }
    var parentId by remember { mutableStateOf<String?>(null) }
    var stack by remember { mutableStateOf(listOf<Pair<String, String?>>()) }
    var emby by remember { mutableStateOf<EmbyClient?>(null) }
    var plex by remember { mutableStateOf<PlexClient?>(null) }
    var collectionId by remember { mutableStateOf<String?>(null) }

    var strip by remember { mutableStateOf(Strip.FILES) }
    var sortBy by remember { mutableStateOf(EmbyClient.SortBy.NAME) }
    var ascending by remember { mutableStateOf(true) }
    var grid by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(server, path, parentId, strip, sortBy, ascending) {
        loading = true
        error = null
        runCatching {
            if (server.isEmbyLike) {
                val client = emby ?: EmbyClient(server).also { it.authenticate(); emby = it }
                when (strip) {
                    Strip.FILES ->
                        if (parentId == null) client.views()
                        else client.items(parentId, sortBy = sortBy, ascending = ascending)
                    Strip.RESUME -> client.resume()
                    Strip.LATEST -> client.latest()
                    Strip.COLLECTIONS ->
                        if (collectionId == null) client.collections(null)
                        else client.items(collectionId!!)
                }
            } else if (server.type == "smb") {
                val target = SmbTarget(
                    host = server.address.substringAfter("smb://").substringBefore('/').substringBefore(':'),
                    share = server.address.substringAfter("smb://").substringAfter('/', "").substringBefore('/'),
                    path = path,
                    user = server.username,
                    password = server.password,
                    domain = null
                )
                val authority = buildString {
                    if (target.user.isNotBlank()) {
                        append(encodeUserInfo(target.user))
                        if (target.password.isNotBlank()) append(":" + encodeUserInfo(target.password))
                        append('@')
                    }
                    append(target.host)
                }
                listSmbDirectory(target).map { entry ->
                    MediaEntry(
                        name = entry.name,
                        url = "smb://$authority/${target.share}${entry.url}",
                        isDirectory = entry.isDirectory,
                        size = entry.size
                    )
                }.sortedWith(compareByDescending<MediaEntry> { it.isDirectory }.thenBy { it.name })
            } else if (server.type == "plex") {
                val client = plex ?: PlexClient(server).also { plex = it }
                val libraryId = if (path.isBlank()) client.libraries().firstOrNull()?.id ?: "" else path
                val items = when (strip) {
                    Strip.RESUME -> client.onDeck()
                    Strip.LATEST -> client.recentlyAdded()
                    Strip.COLLECTIONS -> when (val key = collectionId) {
                        null -> client.collections(libraryId).map { it.toItem() }
                        else -> client.itemsInCollection(key)
                    }
                    else -> client.items(libraryId)
                }
                items.map { item ->
                    MediaEntry(
                        name = item.title,
                        url = client.streamUrl(item) ?: "",
                        isDirectory = false,
                        size = item.partSize,
                        entryId = item.id,
                        durationTicks = item.durationMs * 10_000,
                        played = item.isPlayed,
                        positionMs = item.viewOffsetMs,
                        year = item.year,
                        imageUrl = client.artworkUrl(item)
                    )
                }
            } else if (server.type == "s3") {
                val target = com.senplay.net.S3Target.of(server)
                    ?: error("S3 配置不完整：地址需为 bucket@host/前缀，账号填 AccessKey，密码填 SecretKey")
                com.senplay.net.S3.list(target, path.ifBlank { null }).map { object3 ->
                    MediaEntry(
                        name = object3.name,
                        url = object3.key,
                        isDirectory = object3.isDirectory,
                        size = object3.size
                    )
                }
            } else if (server.type == "sftp") {
                val authority = server.address
                    .removePrefix("sftp://").removePrefix("SFTP://")
                    .substringBefore('/')
                listSftpDirectory(server, path).map { entry ->
                    MediaEntry(
                        name = entry.name,
                        url = "sftp://${withCredentials(authority, server)}${entry.url}",
                        isDirectory = entry.isDirectory,
                        size = entry.size
                    )
                }
            } else if (server.type == "ftp") {
                val ftpAuthority = server.address.removePrefix("ftp://").removePrefix("FTP://")
                    .substringBefore('/')
                listFtpDirectory(server, path).map { entry ->
                    MediaEntry(
                        name = entry.name,
                        url = "ftp://${withCredentials(ftpAuthority, server)}${entry.url}",
                        isDirectory = entry.isDirectory,
                        size = entry.size
                    )
                }
            } else {
                WebDavClient.list(server, path)
            }
        }.onSuccess { entries = it }
            .onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    val query = search.trim()
    val visible = if (query.isEmpty()) {
        entries
    } else {
        entries.filter { it.name.contains(query, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                val previous = stack.lastOrNull()
                if (previous == null) {
                    onBack()
                } else {
                    stack = stack.dropLast(1)
                    path = previous.first
                    parentId = previous.second
                }
            }) { Text("< 返回") }
            Text(
                server.name.ifBlank { server.address },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (server.isEmbyLike) {
                TextButton(onClick = { menuOpen = true }) { Text(strip.title) }
            }
            IconButton(onClick = { grid = !grid }) {
                Icon(
                    if (grid) Icons.Filled.List else Icons.Filled.GridView,
                    contentDescription = "切换布局"
                )
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("搜索") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )

        error?.let {
            Text(
                "错误：$it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (loading && entries.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                Text("载入中…", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (!loading && visible.isEmpty()) {
            Text(
                if (server.isEmbyLike) "这个位置没有内容。" else "空目录。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp)
            )
        }

        val queue = visible.filter { !it.isDirectory }.map { entry ->
            val url = if (server.isEmbyLike) {
                (emby ?: EmbyClient(server)).streamUrl(entry)
            } else {
                entry.url
            }
            url to entry.name
        }

        // Sidecar subtitles sharing the media file's stem. Simplified Chinese wins,
        // then traditional, then anything else, then plain srt last.
        fun matchSubtitle(entry: MediaEntry): String? {
            val stem = entry.name.substringBeforeLast('.', entry.name).lowercase()
            val extensions = setOf("srt", "ass", "ssa", "vtt", "sub")
            val candidates = entries.filter { candidate ->
                !candidate.isDirectory &&
                    candidate.name.substringAfterLast('.', "").lowercase() in extensions &&
                    candidate.name.substringBeforeLast('.', candidate.name).lowercase().startsWith(stem)
            }
            val chosen = candidates.minByOrNull { candidate ->
                val lower = candidate.name.lowercase()
                when {
                    lower.contains("chs") || lower.contains("sc") ||
                        lower.contains("简体") || lower.contains("zh-cn") -> 0
                    lower.contains("cht") || lower.contains("tc") ||
                        lower.contains("繁体") || lower.contains("zh-tw") -> 1
                    lower.contains("eng") -> 3
                    else -> 2
                }
            } ?: return null
            return if (server.isEmbyLike) {
                (emby ?: EmbyClient(server)).streamUrl(chosen)
            } else {
                chosen.url
            }
        }

        fun open(entry: MediaEntry) {
            if (strip == Strip.COLLECTIONS && collectionId == null && server.isEmbyLike && entry.isDirectory) {
                collectionId = entry.entryId ?: entry.url
                return
            }
            val navigable = stack.isEmpty() || !server.isEmbyLike || strip == Strip.FILES
            if (server.isEmbyLike && strip == Strip.FILES && entry.isDirectory) {
                stack = stack + (path to parentId)
                parentId = entry.entryId
            } else if (!server.isEmbyLike && entry.isDirectory) {
                stack = stack + (path to parentId)
                path = when (server.type) {
                    "smb" -> entry.url.substringAfter("smb://").substringAfter('/', "")
                    "ftp" -> entry.url.substringAfter("ftp://").substringAfter('/', "/")
                    "sftp" -> entry.url.substringAfter("sftp://").substringAfter('/', "/")
                    else -> entry.url
                }
            } else {
                val subtitle = matchSubtitle(entry)
                val request = if (server.type == "s3") {
                    val target = com.senplay.net.S3Target.of(server)
                    val signed = target?.let { com.senplay.net.S3.presignedUrl(it, entry.url) }
                    if (signed == null) {
                        error = "无法生成 S3 下载地址"
                        return
                    }
                    PlayRequest(
                        url = signed,
                        name = entry.name,
                        subtitleUrl = subtitle,
                        positionMs = entry.positionMs,
                        queue = queue
                    )
                } else if (server.type == "plex") {
                    PlayRequest(
                        url = entry.url,
                        name = entry.name,
                        subtitleUrl = subtitle,
                        positionMs = entry.positionMs,
                        headers = plex?.mediaHeaders() ?: emptyMap(),
                        queue = queue
                    )
                } else if (server.isEmbyLike) {
                    val client = emby ?: EmbyClient(server)
                    PlayRequest(
                        url = client.streamUrl(entry),
                        name = entry.name,
                        subtitleUrl = subtitle,
                        positionMs = entry.positionMs,
                        headers = client.mediaHeaders(),
                        queue = queue
                    )
                } else {
                    PlayRequest(
                        url = entry.url,
                        name = entry.name,
                        subtitleUrl = subtitle,
                        positionMs = entry.positionMs,
                        queue = queue
                    )
                }
                onPlay(request)
            }
            @Suppress("UNUSED_EXPRESSION") navigable
        }

        if (grid) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 118.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(10.dp)
            ) {
                gridItems(visible, key = { it.url + it.name }) { entry ->
                    PosterCell(entry) { open(entry) }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(visible, key = { it.url + it.name }) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.name, maxLines = 2) },
                        supportingContent = entry.subtitle.ifBlank { null }?.let { text ->
                            { Text(text, style = MaterialTheme.typography.bodySmall) }
                        },
                        leadingContent = {
                            Icon(
                                if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.clickable { open(entry) }
                    )
                }
            }
        }
    }

    if (menuOpen) {
        AlertDialog(
            onDismissRequest = { menuOpen = false },
            title = { Text("浏览设置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("板块", style = MaterialTheme.typography.labelMedium)
                    Strip.values().forEach { option ->
                        TextButton(onClick = {
                            strip = option
                            stack = emptyList()
                            path = ""
                            parentId = null
                        }) {
                            Text(
                                (if (strip == option) "✓ " else "") + option.title,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text("排序", style = MaterialTheme.typography.labelMedium)
                    EmbyClient.SortBy.values().forEach { option ->
                        TextButton(onClick = { sortBy = option }) {
                            Text(
                                (if (sortBy == option) "✓ " else "") + option.title,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    TextButton(onClick = { ascending = !ascending }) {
                        Text(if (ascending) "✓ 升序" else "✓ 降序")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuOpen = false }) { Text("完成") }
            }
        )
    }
}

/** Percent-encodes a credential so an `@` or `:` inside it cannot break the URL. */
private fun encodeUserInfo(value: String): String =
    java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

private fun withCredentials(authority: String, server: ServerConfig): String {
    if (server.username.isBlank()) return authority
    val credentials = buildString {
        append(encodeUserInfo(server.username))
        if (server.password.isNotBlank()) append(":" + encodeUserInfo(server.password))
        append('@')
    }
    return credentials + authority
}

private enum class Strip(val title: String) {
    FILES("文件"),
    RESUME("继续观看"),
    LATEST("最近添加"),
    COLLECTIONS("合集")
}

/** A collection has no media of its own, only a title and a key. */
private fun PlexLibrary.toItem() = PlexItem(
    id = id,
    title = title,
    year = null,
    durationMs = 0L,
    thumbPath = null,
    partPath = null,
    partSize = 0L,
    viewOffsetMs = 0L,
    isPlayed = false
)

@Composable
private fun PosterCell(entry: MediaEntry, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(bottom = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val url = entry.imageUrl
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = url,
                    contentDescription = entry.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.Movie,
                    contentDescription = null
                )
            }
            if (entry.progress > 0f) {
                LinearProgressIndicator(
                    progress = { entry.progress },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                )
            }
        }
        Text(
            entry.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (entry.subtitle.isNotBlank()) {
            Text(
                entry.subtitle,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AddServerDialog(onDismiss: () -> Unit, onSave: (ServerConfig) -> Unit) {
    var type by remember { mutableStateOf(SERVER_TYPES.first()) }
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加服务器") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SERVER_TYPES.forEach { option ->
                        OutlinedButton(onClick = { type = option }) {
                            Text(if (type == option) "• " + option else option)
                        }
                    }
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("地址，例如 https://nas.example.com:5006") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (address.isNotBlank()) {
                        onSave(
                            ServerConfig(
                                id = UUID.randomUUID().toString(),
                                type = type,
                                name = name.ifBlank { type },
                                address = address.trim().trimEnd('/'),
                                username = username,
                                password = password
                            )
                        )
                    }
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
