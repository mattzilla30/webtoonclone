package com.dexter.platform

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Phone-side glue for the Wear OS companion. [WatchLink] pushes page turns here and the
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
     * Called by [WatchLink] on [WearPaths.PAGE_PREVIOUS]/[WearPaths.PAGE_NEXT].
     * Never blocks; the buffer holds a burst of taps while the reader catches up.
     */
    fun requestTurn(turn: PageTurn) {
        _turns.tryEmit(turn)
    }

    /**
     * Sends the open chapter's progress to the connected watch over [WatchLink], so its
     * at-a-glance view follows the reader. Does nothing when no watch is connected.
     */
    fun publishProgress(title: String, chapterNumber: String, page: Int, total: Int) {
        WatchLink.broadcast(WearPaths.PROGRESS_REPLY, wearProgressPayload(title, chapterNumber, page, total))
    }
}
