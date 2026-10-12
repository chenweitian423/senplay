package com.senplay.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.senplay.core.HistoryEntry
import com.senplay.core.LibraryStore
import com.senplay.core.PlayRequest
import com.senplay.core.ServerConfig
import com.senplay.core.Thumbnails
import com.senplay.core.UrlListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The landing tab: what you were watching and where it can be watched from.
 *
 * The reference leads with a poster carousel. There is no artwork catalogue behind
 * this tree, so the top shelf is the resume row — a frame from the file itself where
 * that frame can be read, and the same information the carousel would carry.
 */
@Composable
fun HomeTabScreen(
    store: LibraryStore,
    onPlay: (PlayRequest) -> Unit,
    onOpenServers: () -> Unit
) {
    val history = remember { store.history() }
    val servers = remember { store.servers() }
    val urls = remember { store.urlList() }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (history.isEmpty() && servers.isEmpty() && urls.isEmpty()) {
            Text(
                "还没有内容。在「媒体库」里添加服务器或 URL，或把视频放进设备存储。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp)
            )
        }

        if (history.isNotEmpty()) {
            Shelf("继续观看") {
                items(history.take(12), key = { it.url }) { entry ->
                    ResumeCard(entry) {
                        onPlay(
                            PlayRequest(
                                url = entry.url,
                                name = entry.name,
                                positionMs = entry.positionMs,
                                imageUrl = entry.imageUrl,
                                durationMs = entry.durationMs
                            )
                        )
                    }
                }
            }
        }

        if (servers.isNotEmpty()) {
            Shelf("我的媒体") {
                items(servers, key = { it.id }) { server ->
                    ServerTile(server, onClick = onOpenServers)
                }
            }
        }

        if (urls.isNotEmpty()) {
            Shelf("URL 列表") {
                items(urls.take(12), key = { it.url }) { item ->
                    UrlTile(item) {
                        onPlay(
                            PlayRequest(
                                url = item.url,
                                name = item.name,
                                userAgent = item.userAgent,
                                positionMs = store.savedPosition(item.url)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Shelf(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp)
    )
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun ResumeCard(entry: HistoryEntry, onClick: () -> Unit) {
    val context = LocalContext.current
    // The poster the browser showed when the entry carries one; a frame from the file
    // when it is one we can read; otherwise the tile stands in. Reading a frame off a
    // remote URL is not something this can do, which is why the artwork is recorded at
    // play time instead.
    val frame by produceState<android.graphics.Bitmap?>(initialValue = null, entry.url, entry.imageUrl) {
        value = if (entry.imageUrl != null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { Thumbnails.frame(context, entry.url, 10) }.getOrNull()
            }
        }
    }

    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(90.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val artwork = entry.imageUrl
            val bitmap = frame
            if (artwork != null) {
                AsyncImage(
                    model = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(90.dp)
                )
            } else if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(90.dp)
                )
            } else {
                Icon(Icons.Filled.Movie, contentDescription = null)
            }
        }
        LinearProgressIndicator(
            progress = { entry.progress.toFloat() },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            entry.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ServerTile(server: ServerConfig, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(90.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Dns, contentDescription = null)
        }
        Text(
            server.name.ifBlank { server.address },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            server.type.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UrlTile(item: UrlListItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(90.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Link, contentDescription = null)
        }
        Text(
            item.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            item.url,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
