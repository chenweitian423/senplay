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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.senplay.core.LibraryStore
import com.senplay.core.PlayRequest

/**
 * One search across everything the app can open: the URL list and the saved servers.
 * The reference's search tab spans its whole library; these are this tree's
 * equivalents, and each hit acts directly — a URL entry plays from where it left off.
 */
@Composable
fun SearchScreen(store: LibraryStore, onPlay: (PlayRequest) -> Unit) {
    var query by remember { mutableStateOf("") }
    val urls = remember { store.urlList() }
    val servers = remember { store.servers() }

    val needle = query.trim()
    val urlHits = if (needle.isEmpty()) {
        emptyList()
    } else {
        urls.filter {
            it.name.contains(needle, ignoreCase = true) || it.url.contains(needle, ignoreCase = true)
        }
    }
    val serverHits = if (needle.isEmpty()) {
        emptyList()
    } else {
        servers.filter {
            it.name.contains(needle, ignoreCase = true) || it.address.contains(needle, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            singleLine = true,
            label = { Text("搜索片名、地址、服务器") }
        )

        if (needle.isEmpty()) {
            Hint("输入片名、地址或服务器名。")
        } else if (urlHits.isEmpty() && serverHits.isEmpty()) {
            Hint("没有匹配「$needle」的条目。")
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(urlHits, key = { it.url }) { item ->
                ListItem(
                    headlineContent = {
                        Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(item.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            items(serverHits, key = { it.id }) { server ->
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
        text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}
