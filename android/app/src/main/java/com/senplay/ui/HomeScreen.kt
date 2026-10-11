package com.senplay.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(store: LibraryStore) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf(
        Tab("媒体库", Icons.Filled.VideoLibrary),
        Tab("服务器", Icons.Filled.Dns),
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
                else -> SettingsScreen(store)
            }
        }
    }
}
