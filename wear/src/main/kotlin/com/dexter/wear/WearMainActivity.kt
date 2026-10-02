package com.dexter.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.Wearable

/**
 * SCAFFOLD: Wear OS companion. Shows what the phone is reading at a glance and works as a
 * page-turn remote: the two buttons send [PAGE_NEXT]/[PAGE_PREVIOUS] messages to the phone,
 * which answers progress on [PROGRESS_REPLY]. Message paths must match the phone's
 * `com.dexter.platform.WearPaths`; they are duplicated here so the wear module builds standalone.
 */
class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { WatchScreen(::sendPageTurn) } }
    }

    private fun sendPageTurn(path: String) {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            val client = Wearable.getMessageClient(this)
            nodes.forEach { client.sendMessage(it.id, path, null) }
        }
    }

    private fun requestProgress() {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            val client = Wearable.getMessageClient(this)
            nodes.forEach { client.sendMessage(it.id, PROGRESS_REQUEST, null) }
        }
        // The reply arrives via a WearableListenerService; this scaffold reads the last known
        // value back through the Data Layer instead of holding a listener. See the TODO below.
    }

    companion object {
        const val PAGE_PREVIOUS = "/dexter/page/previous"
        const val PAGE_NEXT = "/dexter/page/next"
        const val PROGRESS_REQUEST = "/dexter/progress/request"
        const val PROGRESS_REPLY = "/dexter/progress"
    }
}

@Composable
private fun WatchScreen(onTurn: (String) -> Unit) {
    var status by remember { mutableStateOf("Dexter remote") }
    LaunchedEffect(Unit) {
        // TODO: register a WearableListenerService for PROGRESS_REPLY and render seriesTitle,
        // chapterNumber, and a progress bar from the payload.
    }
    Column(
        Modifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(status, style = MaterialTheme.typography.caption3)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = { onTurn(WearMainActivity.PAGE_PREVIOUS); status = "Previous page" }) {
                Text("<")
            }
            Button(onClick = { onTurn(WearMainActivity.PAGE_NEXT); status = "Next page" }) {
                Text(">")
            }
        }
    }
}
