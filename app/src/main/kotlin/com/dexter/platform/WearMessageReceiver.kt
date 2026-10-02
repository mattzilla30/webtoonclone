package com.dexter.platform

import com.dexter.data.LibraryStore
import com.dexter.data.ProgressStore
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Phone-side Wear OS receiver. The watch sends [WearPaths.PAGE_PREVIOUS] / [WearPaths.PAGE_NEXT]
 * to turn pages in the open reader, and [WearPaths.PROGRESS_REQUEST] to ask what is being read;
 * the phone answers on [WearPaths.PROGRESS_REPLY].
 *
 * SCAFFOLD: this compiles once the app module gains the `play-services-wearable` dependency and
 * the manifest entry from the integration snippet in the task report. Page-turn delivery to the
 * reader itself is the remaining hook: broadcast the turn request (e.g. a local broadcast the
 * reader screen collects) so it works while the reader is open.
 */
class WearMessageReceiver : WearableListenerService(), KoinComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val progressStore: ProgressStore by inject()
    private val libraryStore: LibraryStore by inject()

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearPaths.PAGE_PREVIOUS, WearPaths.PAGE_NEXT -> {
                // TODO: forward to the open reader, e.g. via a local broadcast the reader collects.
            }
            WearPaths.PROGRESS_REQUEST -> scope.launch {
                runCatching {
                    val progress = progressStore.export()
                    val last = libraryStore.current().recent.firstOrNull()
                    val payload = if (last != null && last.id in progress) {
                        val parsed = com.dexter.data.parseProgress(progress.getValue(last.id))
                        wearProgressPayload(
                            last.title,
                            last.chapterNumber ?: "",
                            parsed.page,
                            parsed.total,
                        )
                    } else {
                        ""
                    }
                    val nodesTask = Wearable.getNodeClient(applicationContext).connectedNodes
                    nodesTask.addOnSuccessListener { nodes ->
                        val messageClient = Wearable.getMessageClient(applicationContext)
                        nodes.forEach { node ->
                            messageClient.sendMessage(node.id, WearPaths.PROGRESS_REPLY, payload.toByteArray())
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
