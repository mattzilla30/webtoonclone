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
 * Page turns are pushed into [WearBridge.turns], which the reader screen collects while a chapter
 * is open and applies like a volume-key press. Reading progress is pushed to the watch through
 * [WearBridge.publishProgress] when the reader saves.
 */
class WearMessageReceiver : WearableListenerService(), KoinComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val progressStore: ProgressStore by inject()
    private val libraryStore: LibraryStore by inject()

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearPaths.PAGE_PREVIOUS, WearPaths.PAGE_NEXT -> {
                val turn = if (event.path == WearPaths.PAGE_NEXT) WearBridge.PageTurn.Next else WearBridge.PageTurn.Previous
                WearBridge.requestTurn(turn)
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
