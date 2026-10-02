package com.dexter.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Watch-side Data Layer receiver. The phone answers [WearMainActivity]'s progress requests on
 * [PROGRESS_REPLY]; the payload lands here and is published through [WatchState] for the
 * activity to render. Register in the wear manifest with the `BIND_LISTENER` action.
 */
class WearProgressListener : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == PROGRESS_REPLY) {
            WatchState.onProgressReply(event.data)
        }
    }

    companion object {
        /** Phone -> wear: the current reading state, as `seriesTitle|chapterNumber|page|total`. */
        const val PROGRESS_REPLY = "/dexter/progress"
    }
}
