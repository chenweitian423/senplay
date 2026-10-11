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
import androidx.compose.material.icons.filled.Delete
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
import com.senplay.net.WebDavClient
import kotlinx.coroutines.launch
import java.util.UUID

private val SERVER_TYPES = listOf("webdav", "emby", "jellyfin")

@Composable
fun ServersScreen(store: LibraryStore, onPlay: (PlayRequest) -> Unit) {
    var servers by remember { mutableStateOf(store.servers()) }
    var showAdd by remember { mutableStateOf(false) }
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
                IconButton(onClick = { showAdd = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "添加服务器")
                }
            }

            if (servers.isEmpty()) {
                Text(
                    "支持 WebDAV、Emby、Jellyfin。也可以用 senplay://importserver?type=webdav&address=... 一步导入。",
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<MediaEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var path by remember { mutableStateOf("") }
    var parentId by remember { mutableStateOf<String?>(null) }
    var stack by remember { mutableStateOf(listOf<Pair<String, String?>>()) }
    var emby by remember { mutableStateOf<EmbyClient?>(null) }

    LaunchedEffect(server, path, parentId) {
        loading = true
        error = null
        runCatching {
            if (server.isEmbyLike) {
                val client = emby ?: EmbyClient(server).also { it.authenticate(); emby = it }
                if (parentId == null) client.views() else client.items(parentId)
            } else {
                WebDavClient.list(server, path)
            }
        }.onSuccess { entries = it }
            .onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
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
                modifier = Modifier.padding(top = 12.dp)
            )
            Text("", modifier = Modifier.padding(8.dp))
        }

        if (loading) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        }

        error?.let {
            Text(
                "错误：$it",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp)
            )
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(entries, key = { it.url + it.name }) { entry ->
                ListItem(
                    headlineContent = { Text(entry.name, maxLines = 2) },
                    supportingContent = {
                        if (!entry.isDirectory) {
                            Text(if (entry.size > 0) (entry.size / 1024 / 1024).toString() + " MB" else "媒体文件")
                        }
                    },
                    leadingContent = {
                        Icon(
                            if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable {
                        if (entry.isDirectory) {
                            stack = stack + (path to parentId)
                            if (server.isEmbyLike) {
                                parentId = entry.entryId
                            } else {
                                path = entry.url
                            }
                        } else {
                            // Siblings of this folder, so playback can continue on its own.
                            val queue = entries.filter { !it.isDirectory }.map { candidate ->
                                val candidateUrl = if (server.isEmbyLike) {
                                    emby?.streamUrl(candidate) ?: candidate.url
                                } else {
                                    candidate.url
                                }
                                candidateUrl to candidate.name
                            }
                            val request = if (server.isEmbyLike) {
                                val client = emby
                                PlayRequest(
                                    url = client?.streamUrl(entry) ?: entry.url,
                                    name = entry.name,
                                    headers = client?.mediaHeaders() ?: emptyMap(),
                                    queue = queue
                                )
                            } else {
                                PlayRequest(entry.url, entry.name, queue = queue)
                            }
                            onPlay(request)
                        }
                    }
                )
            }
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
