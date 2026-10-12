package com.senplay.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.senplay.core.LibraryStore
import com.senplay.player.PlayerLauncher

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

/**
 * "默认启动模块" — the reference app's module names, mapped onto this app's five
 * tabs. "文件" is the local library, which is tab 0 here and has no setting entry.
 */
private fun initialTab(store: LibraryStore): Int =
    when (store.prefString("general.defaultModule", "files")) {
        "servers" -> 1
        "iptv" -> 2
        "record" -> 3
        else -> 0
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(store: LibraryStore) {
    val context = LocalContext.current
    // "默认启动模块" is about where the app opens, so it is read once here rather than
    // moving the tab whenever the setting changes.
    var tab by remember { mutableStateOf(initialTab(store)) }
    val tabs = listOf(
        Tab("媒体库", Icons.Filled.VideoLibrary),
        Tab("服务器", Icons.Filled.Dns),
        Tab("IPTV", Icons.Filled.Tv),
        Tab("记录", Icons.Filled.History),
        Tab("设置", Icons.Filled.Settings)
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text(tabs[tab].label) }) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> LibraryScreen(store) { PlayerLauncher.start(context, it) }
                1 -> ServersScreen(store) { PlayerLauncher.start(context, it) }
                2 -> IPTVScreen(store) { PlayerLauncher.start(context, it) }
                3 -> HistoryScreen(store) { PlayerLauncher.start(context, it) }
                else -> SettingsScreen(store)
            }
        }
    }
}
