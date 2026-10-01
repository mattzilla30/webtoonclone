package com.dexter.notify

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dexter.DexterApp
import com.dexter.data.Chapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val ACTION_CANCEL_ALL = "com.dexter.action.CANCEL_ALL_DOWNLOADS"
const val ACTION_DOWNLOAD = "com.dexter.action.DOWNLOAD_CHAPTER"

/** Notification buttons for downloads: "Cancel all" on the progress notification, "Download" on a new chapter. */
class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as DexterApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ACTION_CANCEL_ALL -> app.downloadStore.cancelAll()
                    ACTION_DOWNLOAD -> {
                        val seriesId = intent.getStringExtra(EXTRA_SERIES_ID) ?: return@launch
                        val chapterId = intent.getStringExtra(EXTRA_CHAPTER_ID) ?: return@launch
                        val chapter = Chapter(chapterId, intent.getStringExtra(EXTRA_CHAPTER_NUMBER).orEmpty(), "", "")
                        val wifiOnly = app.settingsStore.current().downloadWifiOnly
                        DownloadWorker.enqueue(app, app.downloadStore, seriesId, intent.getStringExtra(EXTRA_TITLE).orEmpty(), intent.getStringExtra(EXTRA_COVER), chapter, wifiOnly)
                        context.getSystemService(NotificationManager::class.java).cancel(seriesId.hashCode())
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
