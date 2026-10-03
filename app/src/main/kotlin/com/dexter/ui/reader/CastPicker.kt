package com.dexter.ui.reader

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexter.cast.CastDevice
import com.dexter.cast.CastManager

/**
 * Lists the TVs on your Wi-Fi: Chromecasts and DLNA smart TVs. Picking one connects; while casting,
 * the dialog offers to stop instead. Asks for local network access first, which Android 17 requires.
 */
@Composable
internal fun CastPicker(manager: CastManager, onDismiss: () -> Unit) {
    val devices by manager.devices.collectAsStateWithLifecycle()
    val connected by manager.connected.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { manager.startDiscovery() }
    // Discovery runs only while the picker is open. It starts after the permission answer, granted or not.
    LaunchedEffect(Unit) { permission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) }
    DisposableEffect(Unit) { onDispose { manager.stopDiscovery() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (connected != null) "Casting to ${connected!!.name}" else "Cast to a TV") },
        text = {
            Column {
                if (connected == null) {
                    if (devices.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Looking for Chromecasts and smart TVs on this Wi-Fi...", modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                    devices.forEach { device ->
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                manager.connect(device)
                                onDismiss()
                            }.padding(vertical = 12.dp),
                        ) {
                            Text(device.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (device is CastDevice.Chromecast) "Chromecast" else "Smart TV (DLNA)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Text("Pages you turn show on the TV. Saved chapters stay on this phone, so only online pages cast.")
                }
            }
        },
        confirmButton = {
            if (connected != null) {
                TextButton(onClick = {
                    manager.endSession()
                    onDismiss()
                }) { Text("Stop casting") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
