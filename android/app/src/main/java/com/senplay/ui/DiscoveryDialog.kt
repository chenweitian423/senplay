package com.senplay.ui

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.senplay.core.LibraryStore
import com.senplay.core.ServerConfig

data class DiscoveredService(
    val name: String,
    val host: String,
    val port: Int,
    val type: String
) {
    val address: String
        get() = if (port > 0) "http://$host:$port" else "http://$host"

    val kindHint: String
        get() {
            val lower = name.lowercased()
            return when {
                lower.contains("jellyfin") -> "Jellyfin"
                lower.contains("emby") -> "Emby"
                lower.contains("plex") -> "Plex"
                lower.contains("fnos") || lower.contains("飞牛") -> "飞牛 fnOS"
                lower.contains("webdav") || lower.contains("dav") -> "WebDAV"
                lower.contains("samba") || lower.contains("smb") -> "SMB"
                lower.contains("nas") || lower.contains("synology") || lower.contains("qnap") -> "NAS"
                else -> "HTTP 服务"
            }
        }
}

/**
 * mDNS discovery through NsdManager. Deliberately DNS-SD rather than SSDP: it needs
 * no extra permission on either platform, and every media server worth connecting
 * to advertises `_http._tcp` anyway.
 */
@Composable
fun DiscoveryDialog(store: LibraryStore, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var services by remember { mutableStateOf<List<DiscoveredService>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress ?: return
                val found = DiscoveredService(
                    name = serviceInfo.serviceName,
                    host = host,
                    port = serviceInfo.port,
                    type = serviceInfo.serviceType
                )
                services = (services + found).distinctBy { it.name + it.host + it.port }
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                error = "无法开始扫描（错误码 $errorCode）"
                scanning = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String) {}

            override fun onDiscoveryStopped(serviceType: String) {
                scanning = false
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                runCatching { nsd?.resolveService(serviceInfo, resolveListener) }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                services = services.filterNot { it.name == serviceInfo.serviceName }
            }
        }

        val types = listOf("_http._tcp.", "_https._tcp.", "_webdav._tcp.", "_smb._tcp.")
        types.forEach { type ->
            runCatching { nsd?.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discoveryListener) }
        }

        onDispose {
            runCatching { nsd?.stopServiceDiscovery(discoveryListener) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("局域网发现") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (scanning) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Text("正在扫描局域网…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.error)
                }
                if (services.isEmpty() && !scanning && error == null) {
                    Text(
                        "没有发现服务。确认设备与服务器在同一网段后重试。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (services.isNotEmpty()) {
                    Spacer(modifier = Modifier.padding(top = 6.dp))
                    LazyColumn {
                        items(services, key = { it.name + it.host + it.port }) { service ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        store.saveServer(
                                            ServerConfig(
                                                id = java.util.UUID.randomUUID().toString(),
                                                type = "webdav",
                                                name = service.name,
                                                address = service.address,
                                                note = "通过局域网发现 · ${service.kindHint}"
                                            )
                                        )
                                        onDismiss()
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(service.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(service.address, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                Text(service.kindHint, style = MaterialTheme.typography.labelSmall,
                                     color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}
