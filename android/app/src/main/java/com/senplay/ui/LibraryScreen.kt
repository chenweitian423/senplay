package com.senplay.ui

import android.content.ContentUris
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.senplay.core.LibraryStore
import com.senplay.core.PlayRequest
import com.senplay.core.Thumbnails
import com.senplay.core.UrlListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalMedia(val name: String, val uri: String, val durationMs: Long, val sizeBytes: Long)

@Composable
fun LibraryScreen(
    store: LibraryStore,
    onPlay: (PlayRequest) -> Unit,
    /** Servers and IPTV live here rather than in the bottom bar, as the reference has them. */
    onOpen: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var localMedia by remember { mutableStateOf<List<LocalMedia>>(emptyList()) }
    var urlList by remember { mutableStateOf(store.urlList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    /** The three list settings this screen's rows read. */
    val previewPercent = remember { store.prefInt("list.previewPosition", 10) }
    val fillThumbnail = remember { store.prefBool("list.fillThumbnail", true) }
    val listRounded = remember { store.prefBool("list.rounded", true) }

    LaunchedEffect(Unit) {
        localMedia = withContext(Dispatchers.IO) { queryLocalMedia(context) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // The reference files servers and IPTV inside the library rather than in the
        // bottom bar. As entries here they keep the servers screen's whole add /
        // delete / discover flow, which spreading it across a toolbar would scatter.
        ListItem(
            headlineContent = { Text("服务器") },
            supportingContent = { Text("WebDAV / Emby / Jellyfin / Plex / FTP / S3") },
            leadingContent = { Icon(Icons.Filled.Dns, contentDescription = null) },
            modifier = Modifier.clickable { onOpen("servers") }
        )
        ListItem(
            headlineContent = { Text("IPTV") },
            supportingContent = { Text("M3U 播放列表与频道") },
            leadingContent = { Icon(Icons.Filled.Tv, contentDescription = null) },
            modifier = Modifier.clickable { onOpen("iptv") }
        )
        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("URL 列表", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "添加 URL")
            }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(urlList, key = { it.url }) { item ->
                ListItem(
                    headlineContent = { Text(item.name) },
                    supportingContent = { Text(item.url, maxLines = 1) },
                    leadingContent = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = {
                            store.removeUrl(item.url)
                            urlList = store.urlList()
                        }) { Icon(Icons.Filled.Delete, contentDescription = "删除") }
                    },
                    modifier = Modifier.clickable {
                        val position = if (store.rememberProgressEnabled) store.savedPosition(item.url) else 0L
                        onPlay(
                            PlayRequest(
                                item.url, item.name,
                                userAgent = item.userAgent,
                                positionMs = position,
                                queue = urlList.map { it.url to it.name }
                            )
                        )
                    }
                )
            }

            item {
                Text(
                    "本地媒体",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
                )
            }

            if (localMedia.isEmpty()) {
                item {
                    Text(
                        "未发现本地视频。把文件放进设备存储，或用「添加 URL」播放网络地址。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            items(localMedia, key = { it.uri }) { media ->
                ListItem(
                    headlineContent = { Text(media.name, maxLines = 1) },
                    supportingContent = {
                        val seconds = media.durationMs / 1000
                        val mb = media.sizeBytes / 1024 / 1024
                        Text(seconds.toString() + " 秒 · " + mb.toString() + " MB")
                    },
                    leadingContent = {
                        // "视频预览图时间": where in the file the still comes from, and
                        // "缩略图铺满" for how it fills the frame. Zero means no still
                        // at all, which is why the icon stays for that case.
                        val percent = previewPercent
                        var frame by remember(media.uri, percent) {
                            mutableStateOf<Bitmap?>(null)
                        }
                        LaunchedEffect(media.uri, percent) {
                            frame = Thumbnails.frame(context, media.uri, percent)
                        }
                        val still = frame
                        if (still != null) {
                            Image(
                                bitmap = still.asImageBitmap(),
                                contentDescription = null,
                                contentScale = if (fillThumbnail) ContentScale.Crop
                                else ContentScale.Fit,
                                modifier = Modifier
                                    .size(width = 46.dp, height = 66.dp)
                                    .clip(RoundedCornerShape(if (listRounded) 5.dp else 0.dp))
                            )
                        } else {
                            Icon(Icons.Filled.Movie, contentDescription = null)
                        }
                    },
                    modifier = Modifier.clickable {
                        onPlay(
                            PlayRequest(
                                media.uri, media.name,
                                positionMs = if (store.rememberProgressEnabled) store.savedPosition(media.uri) else 0L,
                                queue = localMedia.map { it.uri to it.name }
                            )
                        )
                    }
                )
            }
        }
    }

    if (showAddDialog) {
        var url by remember { mutableStateOf("") }
        var title by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加 URL") },
            text = {
                Column {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("媒体地址") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("显示名称（可留空）") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (url.isNotBlank()) {
                        store.addUrl(UrlListItem(url.trim(), title.ifBlank { url.trim().substringAfterLast('/') }))
                        urlList = store.urlList()
                    }
                    showAddDialog = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("取消") } }
        )
    }
}

private fun queryLocalMedia(context: android.content.Context): List<LocalMedia> {
    val result = mutableListOf<LocalMedia>()
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.SIZE
    )
    runCatching {
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            MediaStore.Video.Media.DATE_ADDED + " DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                result += LocalMedia(
                    name = cursor.getString(nameIndex) ?: "video",
                    uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id).toString(),
                    durationMs = cursor.getLong(durationIndex),
                    sizeBytes = cursor.getLong(sizeIndex)
                )
            }
        }
    }
    return result
}
