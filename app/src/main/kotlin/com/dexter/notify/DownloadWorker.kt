package com.dexter.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dexter.MainActivity
import com.dexter.data.Chapter
import com.dexter.data.DownloadStore
import com.dexter.data.MangaDexRepository
import com.dexter.data.SettingsStore
import com.dexter.data.isWorthRetrying
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

private const val MAX_ATTEMPTS = 4
private const val QUEUE = "downloads"
const val DOWNLOAD_CHANNEL_ID = "downloads"
private const val NOTIFICATION_ID = 7_001
private const val BYTES_PER_MB = 1024L * 1024

/**
 * Saves the chapters in the download queue, one at a time, oldest request first. The queue lives in the
 * database, so it survives a restart, and one chapter can be cancelled or moved without touching the rest.
 * While it works it shows a notification with its progress.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val repository: MangaDexRepository by inject()
    private val store: DownloadStore by inject()
    private val settings: SettingsStore by inject()

    override suspend fun doWork(): Result {
        val notifications = applicationContext.getSystemService(NotificationManager::class.java)
        ensureChannel(applicationContext)
        var foreground = false
        while (true) {
            val item = store.nextQueued() ?: break
            // A chapter queued twice, or saved meanwhile, has nothing left to do.
            if (store.isSaved(item.chapterId)) {
                store.dequeue(item.chapterId)
                continue
            }
            val label = "${item.seriesTitle}, Ep. ${item.number}"
            if (!foreground) {
                // Running in the foreground lifts the ten-minute limit on a long queue. Android refuses it when the
                // app is in the background, and then the queue still runs, only without that guarantee.
                foreground = runCatching { setForeground(foregroundInfo(progressNotification(label, 0, 0))) }.isSuccess
            }
            val chapter = Chapter(item.chapterId, item.number, item.title, item.publishedAt, group = item.groupName, volume = item.volume)
            try {
                val urls = repository.pages(chapter.id, forceRefresh = true)
                // A cancel that landed while the page list was loading never reaches save()'s
                // running job; skip the save instead of downloading a cancelled chapter.
                if (store.consumeCancelled(item.chapterId)) {
                    store.dequeue(item.chapterId)
                    continue
                }
                var lastShown = 0L
                store.save(item.seriesId, item.seriesTitle, item.coverUrl, chapter, urls) { done, total ->
                    // The notification updates a few times a second at most.
                    val now = System.currentTimeMillis()
                    if (now - lastShown > 300 || done == total) {
                        lastShown = now
                        runCatching { notifications.notify(NOTIFICATION_ID, progressNotification(label, done, total)) }
                    }
                }
                store.dequeue(item.chapterId)
                store.enforceCap(settings.current().downloadCapMb * BYTES_PER_MB, keep = item.chapterId)
            } catch (e: CancellationException) {
                // You cancelled this chapter: the queue moves on. Anything else stopping the worker stops the queue.
                if (currentCoroutineContext().isActive && store.consumeCancelled(item.chapterId)) continue
                throw e
            } catch (e: Exception) {
                if (!isWorthRetrying(e) || item.attempts + 1 >= MAX_ATTEMPTS) {
                    // Given up on: the queue moves on to the next chapter.
                    store.dequeue(item.chapterId)
                } else {
                    store.bumpAttempts(item.chapterId)
                    return Result.retry()
                }
            }
        }
        runCatching { notifications.cancel(NOTIFICATION_ID) }
        return Result.success()
    }

    private fun foregroundInfo(notification: Notification) =
        ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

    private fun progressNotification(label: String, done: Int, total: Int): Notification {
        val context = applicationContext
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_ROUTE, "downloads").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelAll = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID,
            Intent(context, DownloadActionReceiver::class.java).setAction(ACTION_CANCEL_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Saving chapters")
            .setContentText(label)
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Cancel all", cancelAll).build())
            .build()
    }

    companion object {
        /** The quiet channel for download progress. */
        fun ensureChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(DOWNLOAD_CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }

        /** Adds [chapter] to the end of the queue and makes sure the queue is running. A chapter already queued keeps its place. */
        suspend fun enqueue(context: Context, store: DownloadStore, seriesId: String, seriesTitle: String, coverUrl: String?, chapter: Chapter, wifiOnly: Boolean) {
            store.enqueue(seriesId, seriesTitle, coverUrl, chapter)
            start(context, wifiOnly)
        }

        /**
         * Starts the worker that drains the queue. A worker already running picks up new chapters itself;
         * the one appended here runs after it, so a chapter queued just as the running worker finds the
         * queue empty still gets saved instead of waiting for the next enqueue.
         */
        fun start(context: Context, wifiOnly: Boolean) {
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
