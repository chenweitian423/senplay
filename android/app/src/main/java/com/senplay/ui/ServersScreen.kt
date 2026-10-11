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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
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
    // LocalContext.current is itself a composable read, so it has to be taken outside
    // the remember block rather than inside it.
    val context = LocalContext.current
    val prefs = remember { LibraryStore.get(context) }
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
                    Strip.LATEST -> {
                        val fetched = client.latest()
                        if (prefs.prefBool("library.hidePlayedInLatest", false)) {
                            fetched.filter { !it.played }
                        } else {
                            fetched
                        }
                    }
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
            // "外挂字幕加载规则". Manual leaves the choice to the subtitle panel.
            val rule = prefs.prefString("subtitle.loadingRule", "sameName")
            if (rule == "manual") return null

            val stem = entry.name.substringBeforeLast('.', entry.name).lowercase()
            val extensions = setOf("srt", "ass", "ssa", "vtt", "sub")
            val candidates = entries.filter { candidate ->
                if (candidate.isDirectory) return@filter false
                if (candidate.name.substringAfterLast('.', "").lowercase() !in extensions) {
                    return@filter false
                }
                // "同文件夹所有字幕" widens the match to every subtitle beside the
                // video. The player attaches one track, so the widening shows up as a
                // looser match rather than as several tracks — that part of the
                // reference app's wording needs a multi-track model this does not have.
                if (rule == "all") return@filter true
                candidate.name.substringBeforeLast('.', candidate.name)
                    .lowercase()
                    .startsWith(stem)
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

        val displayContext = LocalContext.current
    val display = remember(displayContext) {
        ListDisplay(LibraryStore.get(displayContext))
    }

    // A section switch flipped in settings while this screen was open leaves the
    // selection pointing at a strip that is no longer offered.
    LaunchedEffect(display.showContinue, display.showCollection) {
        val available = display.visibleStrips
        if (strip !in available) available.firstOrNull()?.let { strip = it }
    }

    if (grid) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 118.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(10.dp)
            ) {
                gridItems(visible, key = { it.url + it.name }) { entry ->
                    PosterCell(entry, display) { open(entry) }
                }
            }
        } else {
            // This composable has no store of its own; the settings it reads are
            // global, so it reaches for them directly.
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(visible, key = { it.url + it.name }) { entry ->
                    ListItem(
                        headlineContent = {
                            Text(entry.name, maxLines = if (display.ellipsisMode == "none") 2 else 1)
                        },
                        supportingContent = {
                            val detail = rowDetail(entry, display)
                            if (entry.subtitle.isNotBlank() || detail != null) {
                                Column {
                                    if (entry.subtitle.isNotBlank()) {
                                        Text(
                                            entry.subtitle,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    detail?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (display.showProgress && entry.durationMs > 0 &&
                                        entry.positionMs > 0
                                    ) {
                                        LinearProgressIndicator(
                                            progress = {
                                                (entry.positionMs.toFloat() / entry.durationMs)
                                                    .coerceIn(0f, 1f)
                                            },
                                            modifier = Modifier.fillMaxWidth(0.5f)
                                        )
                                    }
                                }
                            }
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
        // This menu is outside the browser's scope, so the flags are read here
        // rather than reached for — same reason the strip list below does it.
        val menuDisplay = ListDisplay(LibraryStore.get(LocalContext.current))
        AlertDialog(
            onDismissRequest = { menuOpen = false },
            title = {
                Text(
                    if (menuDisplay.showTitleCount) "浏览设置（${visible.size} 项）"
                    else "浏览设置"
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("板块", style = MaterialTheme.typography.labelMedium)
                    menuDisplay.visibleStrips.forEach { option ->
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

/**
 * Which of the list-detail toggles are on. Read once when the screen opens, so the
 * row body decides what to draw rather than laying everything out and hiding it.
 */
private class ListDisplay(store: LibraryStore) {
    val showSize = store.prefBool("list.showSize", true)
    val showDuration = store.prefBool("list.showDuration", true)
    val showProgress = store.prefBool("list.showProgress", true)
    /** Title bar only, and off by default: "标题栏显示文件数量". */
    val showTitleCount = store.prefBool("list.showCount", false)
    /** Item counts in the list, from the library group. */
    val showItemCount = store.prefBool("library.showCount", true)
    /** "菜单顶部显示完整名称" — off by default. */
    val fullNameOnMenu = store.prefBool("list.fullNameOnMenu", false)
    /** "文件夹项目显示时间" */
    val showFolderTime = store.prefBool("list.showFolderTime", false)
    /** "显示剩余播放数量" — a count of remaining items, not a duration. */
    val showRemainingCount = store.prefBool("library.showRemaining", true)
    /** "分辨率" and "分辨率简化显示" are one row in two spellings. */
    val showResolution = store.prefBool("list.showResolution", true)
    val simplifyResolution = store.prefBool("list.simplifyResolution", true)
    /** "HDR标记" */
    val showHDRTag = store.prefBool("list.showHDRTag", true)
    /** "帧率" */
    val showFrameRate = store.prefBool("list.showFrameRate", true)
    /** "显示选集指示器" */
    val showEpisodeIndicator = store.prefBool("library.showEpisodeIndicator", true)
    /** "显示演职人员" */
    val showCast = store.prefBool("library.showCast", true)
    /** "中间" / "左边" / "右边" — a choice, not a switch. */
    val ellipsisMode = store.prefString("list.ellipsis", "middle")

    // Poster marks: what the reference app puts on the artwork rather than in the
    // list row.
    val showPlayedMark = store.prefBool("library.showPlayedMark", true)
    val showRating = store.prefBool("library.showRating", true)
    val showRemaining = store.prefBool("library.showRemaining", true)
    val showMediaInfo = store.prefBool("library.showMediaInfo", true)
    val titleCentered = store.prefString("library.titleAlignment", "leading") == "center"
    val showCollection = store.prefBool("library.showCollection", false)
    /** "最新中隐藏已播放" filters inside the strip rather than hiding it. */
    val hidePlayedInLatest = store.prefBool("library.hidePlayedInLatest", false)
    val showContinue = store.prefBool("library.showContinue", true)

    /**
     * The strips whose section the viewer has not switched off. FILES and LATEST have
     * no switch in the reference app's settings and are always available.
     */
    val visibleStrips: List<Strip>
        get() = Strip.values().filter {
            when (it) {
                Strip.RESUME -> showContinue
                Strip.COLLECTIONS -> showCollection
                else -> true
            }
        }
}

/**
 * The secondary line under a row's title, assembled from whatever this entry
 * actually has. Nothing is shown that the source did not supply — a plain WebDAV
 * listing reports a size but no duration, and a directory reports a count instead.
 */
private fun rowDetail(entry: MediaEntry, display: ListDisplay): String? {
    val parts = mutableListOf<String>()
    if (entry.isDirectory) {
        if (display.showItemCount && entry.childCount > 0) parts += "${entry.childCount} 项"
    } else {
        if (display.showSize && entry.size > 0) {
            parts += android.text.format.Formatter.formatFileSize(null, entry.size)
        }
        if (display.showHDRTag && entry.videoRange.isNotBlank() && entry.videoRange != "SDR") {
            parts += entry.videoRange.uppercase()
        }
        if (display.showResolution && entry.width > 0 && entry.height > 0) {
            parts += if (display.simplifyResolution) shortResolution(entry.height)
            else "${entry.width}×${entry.height}"
        }
        if (display.showFrameRate && entry.frameRate > 0) {
            parts += "%.2f fps".format(entry.frameRate)
        }
        if (display.showEpisodeIndicator && entry.episodeNumber > 0) {
            parts += "第 ${entry.episodeNumber} 集"
        }
        if (display.showDuration && entry.durationMs > 0) parts += clock(entry.durationMs)
    }
    if (display.showFolderTime && entry.isDirectory && entry.modifiedAt > 0) {
        parts += java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
            .format(java.util.Date(entry.modifiedAt))
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** Height alone, which is how people quote a resolution: "1080p". */
private fun shortResolution(height: Int): String = when (height) {
    4320 -> "8K"
    2160 -> "4K"
    1440 -> "1440p"
    1080 -> "1080p"
    720 -> "720p"
    480 -> "480p"
    else -> "${height}p"
}

private fun clock(millis: Long): String {
    val total = millis / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, total % 60)
    else "%d:%02d".format(minutes, total % 60)
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

/**
 * Rating, remaining time and size, in the order the reference app shows them. Only
 * what the entry carries: a folder has none of them, and a server that reports no
 * rating produces no line rather than a zero.
 */
private fun posterMark(entry: MediaEntry, display: ListDisplay): String? {
    val parts = mutableListOf<String>()
    if (display.showRating) {
        entry.rating?.takeIf { it > 0 }?.let { parts += "★ %.1f".format(it) }
    }
    if (display.showRemainingCount && entry.unplayedCount > 0) {
        parts += "剩余 ${entry.unplayedCount} 集"
    }
    if (display.showMediaInfo && !entry.isDirectory && entry.size > 0) {
        parts += android.text.format.Formatter.formatFileSize(null, entry.size)
    }
    if (display.showCast && entry.cast.isNotEmpty()) {
        parts += entry.cast.joinToString(" / ")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun PosterCell(entry: MediaEntry, display: ListDisplay, onClick: () -> Unit) {
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
            if (display.showProgress && entry.progress > 0f) {
                LinearProgressIndicator(
                    progress = { entry.progress },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                )
            }
            if (display.showPlayedMark && entry.played) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text("已看完", color = Color.White, fontSize = 9.sp)
                }
            }
        }
        Text(
            entry.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            textAlign = if (display.titleCentered) TextAlign.Center else TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )
        posterMark(entry, display)?.let { mark ->
            Text(
                mark,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        }
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
