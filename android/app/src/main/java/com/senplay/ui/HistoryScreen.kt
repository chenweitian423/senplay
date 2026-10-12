package com.senplay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.senplay.core.LibraryStore
import com.senplay.core.PlayRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Playback history, with the resume position surfaced per row. */
@Composable
fun HistoryScreen(store: LibraryStore, onPlay: (PlayRequest) -> Unit) {
    var history by remember { mutableStateOf(store.history()) }

    fun timecode(ms: Long): String {
        if (ms <= 0) return "00:00"
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    fun relative(epochMs: Long): String {
        if (epochMs <= 0) return ""
        val formatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        return formatter.format(Date(epochMs))
    }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${history.size} 条记录", style = MaterialTheme.typography.bodySmall)
                TextButton(
                    onClick = {
                        store.clearHistory()
                        history = store.history()
                    },
                    enabled = history.isNotEmpty()
                ) { Text("清空") }
            }
        }

        if (history.isEmpty()) {
            item {
                Text(
                    "还没有播放记录。播放过的媒体会出现在这里，并从断点继续。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        items(history, key = { it.url }) { entry ->
            ListItem(
                headlineContent = { Text(entry.name, maxLines = 1) },
                supportingContent = {
                    Column {
                        Text(
                            "${timecode(entry.positionMs)} / ${timecode(entry.durationMs)} · ${relative(entry.updatedAt)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (entry.durationMs > 0) {
                            Spacer(modifier = Modifier.padding(top = 4.dp))
                            LinearProgressIndicator(
                                progress = {
                                    (entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                leadingContent = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                trailingContent = {
                    IconButton(onClick = {
                        store.removeHistory(entry.url)
                        history = store.history()
                    }) { Icon(Icons.Filled.Delete, contentDescription = "删除") }
                },
                modifier = Modifier.clickable {
                    onPlay(
                        PlayRequest(
                            url = entry.url,
                            name = entry.name,
                            positionMs = entry.positionMs,
                            queue = history.map { it.url to it.name },
                            imageUrl = entry.imageUrl,
                            durationMs = entry.durationMs
                        )
                    )
                }
            )
        }

        item { Spacer(modifier = Modifier.padding(12.dp)) }
    }
}
