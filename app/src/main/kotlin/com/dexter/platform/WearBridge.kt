package com.dexter.platform

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Phone-side glue for the Wear OS companion. [WearMessageReceiver] pushes page turns here and the
 * reader publishes its progress through here; the reader screen collects [turns] and applies them
 * to the open chapter, and the watch answers through [publishProgress].
 *
 * A plain object on purpose: no Koin, no stores. Callers pass the values in, so this stays
 * callable from a [android.app.Service] without a scope.
 */
object WearBridge {
    /** One remote page turn, as sent by the watch on [WearPaths.PAGE_PREVIOUS]/[WearPaths.PAGE_NEXT]. */
    enum class PageTurn { Previous, Next }

    private val _turns = MutableSharedFlow<PageTurn>(extraBufferCapacity = 8)

    /**
     * Page turns from the watch, in arrival order. No replay: a turn that lands with no open
     * reader is dropped instead of firing on the next chapter you open.
     */
    val turns: SharedFlow<PageTurn> = _turns.asSharedFlow()

    /**
     * Called by [WearMessageReceiver] on [WearPaths.PAGE_PREVIOUS]/[WearPaths.PAGE_NEXT].
     * Never blocks; the buffer holds a burst of taps while the reader catches up.
     */
    fun requestTurn(turn: PageTurn) {
        _turns.tryEmit(turn)
    }

    /**
     * Fire-and-forget [WearPaths.PROGRESS_REPLY] broadcast to every connected watch, so the
     * watch's at-a-glance view follows the open chapter. Failures are swallowed; the watch can
     * always ask again with [WearPaths.PROGRESS_REQUEST].
     */
    fun publishProgress(context: Context, title: String, chapterNumber: String, page: Int, total: Int) {
        val payload = wearProgressPayload(title, chapterNumber, page, total).toByteArray()
        runCatching {
            val app = context.applicationContext
            Wearable.getNodeClient(app).connectedNodes.addOnSuccessListener { nodes ->
                val client = Wearable.getMessageClient(app)
                nodes.forEach { node ->
                    client.sendMessage(node.id, WearPaths.PROGRESS_REPLY, payload)
                }
            }
        }
    }
}
