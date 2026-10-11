package com.senplay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.senplay.net.Dlna
import com.senplay.net.DlnaDevice

/**
 * Cast target picker. Discovery is SSDP, so a scan takes a few seconds; the dialog
 * says so rather than looking frozen.
 */
@Composable
fun CastDialog(
    onDismiss: () -> Unit,
    onCast: (DlnaDevice) -> Unit
) {
    var devices by remember { mutableStateOf<List<DlnaDevice>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { Dlna.discover() }
            .onSuccess { devices = it }
            .onFailure { error = it.message ?: "搜索失败" }
        scanning = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("投屏") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (scanning) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Text("正在搜索局域网设备…", style = MaterialTheme.typography.bodySmall)
                    }
                }

                error?.let {
                    Text(
                        "错误：$it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (!scanning && devices.isEmpty() && error == null) {
                    Text(
                        "没有找到可投屏的设备。请确认电视或盒子与手机在同一网络，且已开启投屏接收。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                devices.forEach { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCast(device) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.Cast, contentDescription = null)
                        Column {
                            Text(device.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                device.host,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
