package com.senplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.senplay.core.LibraryStore
import com.senplay.core.MediaEntry
import com.senplay.net.EmbyClient

/**
 * One item's own page: artwork, the metadata the list has no room for, and the
 * similar items the server suggests.
 *
 * It asks for the item by id rather than rendering the list row's copy. A row is built
 * from a query that fetches only what a row shows, and fetching by id also means a
 * stale list cannot produce a stale page.
 */
@Composable
fun ItemDetailScreen(
    entry: MediaEntry,
    client: EmbyClient,
    prefs: LibraryStore,
    onPlay: (MediaEntry) -> Unit,
    onBack: () -> Unit
) {
    var detail by remember { mutableStateOf<MediaEntry?>(null) }
    var similar by remember { mutableStateOf<List<MediaEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    val showSimilar = remember { prefs.prefBool("library.showSimilar", true) }
    val showCast = remember { prefs.prefBool("library.showCast", true) }
    val showRating = remember { prefs.prefBool("library.showRating", true) }
    val showResolution = remember { prefs.prefBool("list.showResolution", true) }
    val showHDRTag = remember { prefs.prefBool("list.showHDRTag", true) }

    LaunchedEffect(entry.url) {
        loading = true
        detail = runCatching { client.item(entry.entryId ?: entry.url) }.getOrNull()
        // "显示类似作品" off means the page does not even ask for them.
        similar = if (showSimilar) {
            runCatching { client.similar(entry.entryId ?: entry.url) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        loading = false
    }

    val shown = detail ?: entry

    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                    Text(
                        shown.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    return@Column
                }

                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item { header(shown, showRating, showResolution, showHDRTag, onPlay) }
                    shown.overview?.takeIf { it.isNotBlank() }?.let { text ->
                        item {
                            Text(
                                text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (showCast && shown.cast.isNotEmpty()) {
                        item { labelled("演职人员", shown.cast.joinToString(" / ")) }
                    }
                    if (showSimilar && similar.isNotEmpty()) {
                        item {
                            Column {
                                Text(
                                    "类似作品",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(top = 8.dp)
                                ) {
                                    items(similar, key = { it.url + it.name }) { item ->
                                        Column(
                                            modifier = Modifier
                                                .width(92.dp)
                                                .clickable { onPlay(item) }
                                        ) {
                                            AsyncImage(
                                                model = item.imageUrl,
                                                contentDescription = item.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(width = 92.dp, height = 138.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                    )
                                            )
                                            Text(
                                                item.name,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 2
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun header(
    shown: MediaEntry,
    showRating: Boolean,
    showResolution: Boolean,
    showHDRTag: Boolean,
    onPlay: (MediaEntry) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        AsyncImage(
            model = shown.imageUrl,
            contentDescription = shown.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 110.dp, height = 165.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (showRating && (shown.rating ?: 0.0) > 0) {
                Text(
                    "★ %.1f".format(shown.rating),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            facts(shown, showResolution, showHDRTag)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = { onPlay(shown) }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (shown.positionMs > 0) "继续播放" else "播放")
            }
        }
    }
}

/** Year, runtime, HDR tag and resolution, in the order and spelling the list rows use. */
private fun facts(shown: MediaEntry, showResolution: Boolean, showHDRTag: Boolean): String? {
    val parts = mutableListOf<String>()
    shown.year?.takeIf { it > 0 }?.let { parts += it.toString() }
    if (shown.durationMs > 0) parts += clock(shown.durationMs)
    if (showHDRTag && shown.videoRange.isNotBlank() && shown.videoRange != "SDR") {
        parts += shown.videoRange.uppercase()
    }
    if (showResolution && shown.width > 0 && shown.height > 0) {
        parts += "${shown.width}×${shown.height}"
    }
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}

private fun clock(millis: Long): String {
    val total = (millis / 1000).toInt()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, total % 60)
    } else {
        "%d:%02d".format(minutes, total % 60)
    }
}

@Composable
private fun labelled(title: String, value: String) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
