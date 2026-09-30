package com.webtoonclone.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.webtoonclone.MainActivity
import com.webtoonclone.WebtoonApp
import com.webtoonclone.data.SavedSeries
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "new_chapters"
private const val WORK_NAME = "new-chapters"
const val EXTRA_SERIES_ID = "seriesId"
const val EXTRA_ROUTE = "route"
const val EXTRA_CHAPTER_ID = "chapterId"

/** Checks subscribed series for chapters newer than the last one seen and posts a notification. */
class NewChaptersWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as WebtoonApp
        val settings = app.settingsStore.current()
        // During quiet hours nothing is checked, so the next run after them catches up and notifies once.
        if (settings.quietHours && isQuietHour(LocalTime.now().hour, settings.quietStartHour, settings.quietEndHour)) {
            return Result.success()
        }
        val library = app.libraryStore.data.first()
        val subscribed = library.subscribed
        var failed = false

        for (series in subscribed) {
            val latest = try {
                app.repository.latestChapter(series.id)
            } catch (e: Exception) {
                failed = true
                continue
            }
            val known = series.knownChapterId
            if (latest != null && known != null && latest.id != known && library.notificationsEnabled && series.notify) {
                notify(series, latest.id, latest.number)
            }
            // First sighting only records the chapter, so old chapters never notify. With
            // notifications off the chapter is still recorded, so turning them on stays quiet.
            if (latest != null && (latest.id != known || series.knownChapterNumber == null)) {
                app.libraryStore.markKnown(series.id, latest.id, latest.number)
            }
            delay(300) // stay well under MangaDex's request limit
        }
        return if (failed) Result.retry() else Result.success()
    }

    private fun notify(series: SavedSeries, chapterId: String, chapterNumber: String) {
        val context = applicationContext
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "New chapters", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context,
            series.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_SERIES_ID, series.id)
                putExtra(EXTRA_CHAPTER_ID, chapterId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(series.title)
            .setContentText("Chapter $chapterNumber is out")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(series.id.hashCode(), notification)
    }

    companion object {
        /** Runs every 30 minutes on any network. Safe to call on every launch. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NewChaptersWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
