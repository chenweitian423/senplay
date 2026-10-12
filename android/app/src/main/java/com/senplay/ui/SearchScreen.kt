package com.senplay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.senplay.core.LibraryStore
import com.senplay.core.PlayRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One search across everything the app can open: the URL list, the local media, the
 * IPTV channels and the saved servers. The reference's search tab spans its whole
 * library; these are this tree's equivalents, and each hit acts directly — a URL
 * entry or a file plays, and a channel plays within its group.
 */
@Composable
fun SearchScreen(store: LibraryStore, onPlay: (PlayRequest) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val urls = remember { store.urlList() }
    val servers = remember { store.servers() }
    val channels = remember { loadChannels(store) }
    var localMedia by remember { mutableStateOf<List<LocalMedia>>(emptyList()) }

    // The MediaStore query is a binder call, so it stays off the composition thread.
    // The channel cache and the URL list come out of SharedPreferences, which is
    // cheap enough to read inline.
    LaunchedEffect(Unit) {
        localMedia = withContext(Dispatchers.IO) { queryLocalMedia(context) }
    }

    val needle = query.trim()
    val urlHits = urls.filter {
        needle.isNotEmpty() &&
            (it.name.contains(needle, ignoreCase = true) || it.url.contains(needle, ignoreCase = true))
    }
    val localHits = localMedia.filter {
        needle.isNotEmpty() && it.name.contains(needle, ignoreCase = true)
    }
    val channelHits = channels.filter {
        needle.isNotEmpty() &&
            (it.name.contains(needle, ignoreCase = true) || it.group.contains(needle, ignoreCase = true))
    }
    val serverHits = servers.filter {
        needle.isNotEmpty() &&
            (it.name.contains(needle, ignoreCase = true) || it.address.contains(needle, ignoreCase = true))
    }
    val empty = urlHits.isEmpty() && localHits.isEmpty() &&
        channelHits.isEmpty() && serverHits.isEmpty()

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            singleLine = true,
            label = { Text("搜索片名、频道、地址、服务器") }
        )

        if (needle.isEmpty()) {
            Hint("输入片名、频道、地址或服务器名。")
        } else if (empty) {
            Hint("没有匹配「$needle」的条目。")
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(urlHits, key = { "url-" + it.url }) { item ->
                ListItem(
                    headlineContent = {
                        Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text("URL · " + item.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    modifier = Modifier.clickable {
                        onPlay(
                            PlayRequest(
                                url = item.url,
                                name = item.name,
                                userAgent = item.userAgent,
                                positionMs = store.savedPosition(item.url)
                            )
                        )
                    }
                )
            }
            // Local files and channels carry their siblings as the queue, which is how
            // the library and the IPTV screen hand them over — a hit then behaves like
            // the row it mirrors, next and previous included.
            items(localHits, key = { "local-" + it.uri }) { media ->
                ListItem(
                    headlineContent = {
                        Text(media.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = { Text("本地文件") },
                    modifier = Modifier.clickable {
                        onPlay(
                            PlayRequest(
                                url = media.uri,
                                name = media.name,
                                positionMs = store.savedPosition(media.uri),
                                queue = localMedia.map { it.uri to it.name }
                            )
                        )
                    }
                )
            }
            items(channelHits, key = { "iptv-" + it.url + it.name }) { channel ->
                ListItem(
                    headlineContent = {
                        Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = { Text("IPTV · " + channel.group) },
                    modifier = Modifier.clickable {
                        onPlay(
                            PlayRequest(
                                url = channel.url,
                                name = channel.name,
                                queue = channels.filter { it.group == channel.group }
                                    .map { it.url to it.name }
                            )
                        )
                    }
                )
            }
            items(serverHits, key = { "server-" + it.id }) { server ->
                ListItem(
                    headlineContent = { Text(server.name.ifBlank { server.address }) },
                    supportingContent = {
                        Text(server.type.uppercase() + " · " + server.address, maxLines = 1)
                    }
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}
