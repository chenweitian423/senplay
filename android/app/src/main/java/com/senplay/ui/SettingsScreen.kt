package com.senplay.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.senplay.core.LibraryStore

@Composable
fun SettingsScreen(store: LibraryStore) {
    var remember by remember { mutableStateOf(store.rememberProgressEnabled) }
    var ua by remember { mutableStateOf(store.defaultUserAgent) }
    var historyCount by remember { mutableStateOf(store.history().size) }

    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text("记住播放进度") },
            supportingContent = { Text("退出播放器时记录位置，下次从断点继续") },
            trailingContent = {
                Switch(checked = remember, onCheckedChange = {
                    remember = it
                    store.rememberProgressEnabled = it
                })
            }
        )

        HorizontalDivider()

        ListItem(
            headlineContent = { Text("默认 User-Agent") },
            supportingContent = { Text("供媒体服务器识别客户端；留空使用内置默认值") },
            modifier = Modifier.padding(top = 8.dp)
        )
        OutlinedTextField(
            value = ua,
            onValueChange = {
                ua = it
                store.defaultUserAgent = it.ifBlank { LibraryStore.DEFAULT_UA }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true,
            label = { Text("User-Agent") }
        )

        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))

        ListItem(
            headlineContent = { Text("播放历史") },
            supportingContent = { Text(historyCount.toString() + " 条记录") },
            trailingContent = {
                TextButton(onClick = {
                    store.clearHistory()
                    historyCount = 0
                }) { Text("清空") }
            }
        )

        HorizontalDivider()

        ListItem(
            headlineContent = { Text("关于") },
            supportingContent = {
                Text(
                    "SenPlay 是按公开行为契约自行实现的播放器：URL Scheme、" +
                        "媒体服务器接入方式与交互取自 SenPlayer 6.2.1 的可观察行为，" +
                        "不含其任何代码。"
                )
            }
        )
        Text(
            "版本 1.0.0",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(16.dp)
        )
    }
}
