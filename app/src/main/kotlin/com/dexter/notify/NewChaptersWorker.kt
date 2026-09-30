package com.dexter.notify

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
import com.dexter.DexterApp
import com.dexter.MainActivity
import com.dexter.data.SavedSeries
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "new_chapters"
private const val WORK_NAME = "new-chapters"
private const val GROUP_KEY = "new_chapters_group"
private const val DIGEST_ID = 1
const val EXTRA_SERIES_ID = "seriesId"
const val EXTRA_ROUTE = "route"
const val EXTRA_CHAPTER_ID = "chapterId"
const val EXTRA_CHAPTER_NUMBER = "chapterNumber"
const val EXTRA_TITLE = "title"
const val EXTRA_COVER = "cover"

/** A chapter the background check found for a subscribed series. */
data class NewChapter(val series: SavedSeries, val chapterId: String, val number: String)

/** The notification title for [count] new chapters. */
fun digestTitle(count: Int): String = if (count == 1) "1 new chapter" else "$count new chapters"

/** One line per series for the digest, joining several chapters of one series. Input pairs are title and chapter number. */
fun digestLines(items: List<Pair<String, String>>): List<String> =
    items.groupBy({ it.first }, { it.second }).map { (title, numbers) -> "$title: ${numbers.joinToString(", ") { "Ch. $it" }}" }

/** Checks subscribed series for chapters newer than the last one seen and posts a notification. */
class NewChaptersWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as DexterApp
        val settings = app.settingsStore.current()
        // During quiet hours nothing is checked, so the next run after them catches up and notifies once.
        if (settings.quietHours && isQuietHour(LocalTime.now().hour, settings.quietStartHour, settings.quietEndHour)) {
            return Result.success()
        }
        val library = app.libraryStore.data.first()
        val subscribed = library.subscribed
        var failed = false
        val found = mutableListOf<NewChapter>()

        for (series in subscribed) {
            val latest = try {
                app.repository.latestChapter(series.id)
            } catch (e: Exception) {
                failed = true
                continue
            }
            val known = series.knownChapterId
            if (latest != null && known != null && latest.id != known && library.notificationsEnabled && series.notify) {
                found += NewChapter(series, latest.id, latest.number)
            }
            // First sighting only records the chapter, so old chapters never notify. With
            // notifications off the chapter is still recorded, so turning them on stays quiet.
            if (latest != null && (latest.id != known || series.knownChapterNumber == null)) {
                app.libraryStore.markKnown(series.id, latest.id, latest.number)
            }
            delay(300) // stay well under MangaDex's request limit
        }
        post(found, settings.notificationDigest)
        return if (failed) Result.retry() else Result.success()
    }

    private fun post(found: List<NewChapter>, digest: Boolean) {
        if (found.isEmpty()) return
        val context = applicationContext
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "New chapters", NotificationManager.IMPORTANCE_DEFAULT))
        if (digest) {
            val open = PendingIntent.getActivity(
                context,
                DIGEST_ID,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_ROUTE, "library")
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val lines = digestLines(found.map { it.series.title to it.number })
            manager.notify(
                DIGEST_ID,
                Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_more)
                    .setContentTitle(digestTitle(found.size))
                    .setContentText(lines.first())
                    .setStyle(Notification.InboxStyle().also { style -> lines.forEach(style::addLine) })
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build(),
            )
            return
        }
        found.forEach { item -> manager.notify(item.series.id.hashCode(), single(item)) }
        if (found.size > 1) {
            manager.notify(
                DIGEST_ID,
                Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_more)
                    .setContentTitle(digestTitle(found.size))
                    .setGroup(GROUP_KEY)
                    .setGroupSummary(true)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    private fun single(item: NewChapter): Notification {
        val context = applicationContext
        val series = item.series
        val open = PendingIntent.getActivity(
            context,
            series.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_SERIES_ID, series.id)
                putExtra(EXTRA_CHAPTER_ID, item.chapterId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val markRead = PendingIntent.getBroadcast(
            context,
            series.id.hashCode(),
            Intent(context, MarkReadReceiver::class.java).apply {
                putExtra(EXTRA_SERIES_ID, series.id)
                putExtra(EXTRA_CHAPTER_ID, item.chapterId)
                putExtra(EXTRA_CHAPTER_NUMBER, item.number)
                putExtra(EXTRA_TITLE, series.title)
                putExtra(EXTRA_COVER, series.coverUrl)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(series.title)
            .setContentText("Chapter ${item.number} is out")
            .setContentIntent(open)
            .setGroup(GROUP_KEY)
            .addAction(Notification.Action.Builder(null, "Mark read", markRead).build())
            .setAutoCancel(true)
            .build()
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
