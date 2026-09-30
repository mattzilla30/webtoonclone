package com.dexter.notify

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dexter.DexterApp
import com.dexter.data.SavedSeries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The "Mark read" button on a new chapter notification: records the chapter as read and clears the notification. */
class MarkReadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val seriesId = intent.getStringExtra(EXTRA_SERIES_ID) ?: return
        val chapterId = intent.getStringExtra(EXTRA_CHAPTER_ID) ?: return
        val saved = SavedSeries(
            id = seriesId,
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            coverUrl = intent.getStringExtra(EXTRA_COVER),
            chapterId = chapterId,
            chapterNumber = intent.getStringExtra(EXTRA_CHAPTER_NUMBER),
        )
        context.getSystemService(NotificationManager::class.java).cancel(seriesId.hashCode())
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                (context.applicationContext as DexterApp).libraryStore.recordRecent(saved)
            } finally {
                pending.finish()
            }
        }
    }
}
