package com.senplay.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    /** "显示记录模块" — the index stays at 3 so the other tabs keep their tags; only
     * the item and, below, its screen are skipped. */
    val showRecordModule = remember { store.prefBool("ui.showRecordModule", true) }
    val recordTab = 3
    val tabs = listOf(
        Tab("首页", Icons.Filled.Home),
        Tab("媒体库", Icons.Filled.VideoLibrary),
        Tab("搜索", Icons.Filled.Search),
        Tab("记录", Icons.Filled.History),
        Tab("设置", Icons.Filled.Settings)
    )

    // A tab that is no longer offered must not stay selected.
    LaunchedEffect(showRecordModule) {
        if (!showRecordModule && tab == recordTab) tab = 0
    }

    var nested by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (nested) {
                            "servers" -> "服务器"
                            "iptv" -> "IPTV"
                            else -> tabs[tab].label
                        }
                    )
                },
                navigationIcon = {
                    if (nested != null) {
                        IconButton(onClick = { nested = null }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, item ->
                    if (index == recordTab && !showRecordModule) return@forEachIndexed
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
            // Servers and IPTV are reached from inside the library now, so the tab
            // number alone no longer decides what is on screen.
            when {
                nested == "servers" -> ServersScreen(store) { PlayerLauncher.start(context, it) }
                nested == "iptv" -> IPTVScreen(store) { PlayerLauncher.start(context, it) }
                tab == 0 -> HomeTabScreen(
                    store,
                    onPlay = { PlayerLauncher.start(context, it) },
                    onOpenServers = { nested = "servers" }
                )
                tab == 1 -> LibraryScreen(store, { PlayerLauncher.start(context, it) }) { nested = it }
                tab == 2 -> SearchScreen(store) { PlayerLauncher.start(context, it) }
                tab == recordTab -> HistoryScreen(store) { PlayerLauncher.start(context, it) }
                else -> SettingsScreen(store)
            }
        }
    }
}
