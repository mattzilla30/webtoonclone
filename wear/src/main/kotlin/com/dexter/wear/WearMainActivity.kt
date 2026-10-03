package com.dexter.wear

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.Wearable

/**
 * Wear OS companion. Shows what the phone is reading at a glance and works as a page-turn
 * remote: the two buttons send [PAGE_NEXT]/[PAGE_PREVIOUS] messages to the phone, which answers
 * progress on [PROGRESS_REPLY]. That reply arrives through [WearProgressListener] into
 * [WatchState], which this screen renders.
 *
 * Message paths must match the phone's `com.dexter.platform.WearPaths`; they are duplicated
 * here so the wear module builds standalone. Keep them in sync.
 */
class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { WatchScreen(::sendPageTurn, ::requestProgress) } }
    }

    override fun onResume() {
        super.onResume()
        // Refresh the at-a-glance view every time the watch screen comes up.
        requestProgress()
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
    }

    companion object {
        const val PAGE_PREVIOUS = "/dexter/page/previous"
        const val PAGE_NEXT = "/dexter/page/next"
        const val PROGRESS_REQUEST = "/dexter/progress/request"
        const val PROGRESS_REPLY = "/dexter/progress"
    }
}

@Composable
private fun WatchScreen(onTurn: (String) -> Unit, onRefresh: () -> Unit) {
    val progress by WatchState.progress.collectAsState()

    // Ask the phone what is being read when the screen first appears; the reply flows back
    // through WearProgressListener into WatchState. onResume covers later visits.
    LaunchedEffect(Unit) { onRefresh() }

    Column(
        Modifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val current = progress
        if (current == null) {
            Text(
                "Open a chapter on your phone",
                style = MaterialTheme.typography.caption2,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRefresh, modifier = Modifier.padding(top = 8.dp)) {
                Text("Refresh")
            }
        } else {
            Text(
                current.seriesTitle,
                style = MaterialTheme.typography.title3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                "Ch. ${current.chapterNumber}",
                style = MaterialTheme.typography.caption1,
                modifier = Modifier.padding(top = 2.dp),
            )
            // Wear's Material library has no linear bar, so this draws one: a track and the part read.
            Box(
                Modifier.fillMaxWidth().padding(vertical = 8.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colors.onSurface.copy(alpha = 0.2f)),
            ) {
                Box(Modifier.fillMaxWidth(current.share.coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colors.primary))
            }
            Text(
                "Page ${current.page + 1} of ${current.total}",
                style = MaterialTheme.typography.caption2,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Button(onClick = { onTurn(WearMainActivity.PAGE_PREVIOUS) }) { Text("<") }
            Button(onClick = { onTurn(WearMainActivity.PAGE_NEXT) }) { Text(">") }
        }
    }
}
