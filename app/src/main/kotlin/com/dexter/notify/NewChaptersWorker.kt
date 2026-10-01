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
import com.dexter.data.Order
import com.dexter.data.SavedSeries
import com.dexter.data.SeriesSummary
import com.dexter.data.isWorthRetrying
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** The notification channel for new chapters. Settings links to its system page. */
const val CHANNEL_ID = "new_chapters"
private const val WORK_NAME = "new-chapters"
private const val GROUP_KEY = "new_chapters_group"
private const val DIGEST_ID = 1
private const val FULL_CHECK_MS = 6L * 60 * 60 * 1000
const val EXTRA_SERIES_ID = "seriesId"
const val EXTRA_ROUTE = "route"
const val EXTRA_CHAPTER_ID = "chapterId"
const val EXTRA_CHAPTER_NUMBER = "chapterNumber"
const val EXTRA_TITLE = "title"
const val EXTRA_COVER = "cover"

/** A chapter the background check found for a subscribed series. */
data class NewChapter(val series: SavedSeries, val chapterId: String, val number: String)

/**
 * Whether a subscription needs its chapter feed read: when no chapter is recorded yet, when the newest
 * upload is unknown, or when something was uploaded since the last look.
 */
fun needsFeedCheck(series: SavedSeries, lastUpload: String?, newestUpload: String?): Boolean =
    series.knownChapterId == null || series.knownChapterNumber == null || newestUpload == null || newestUpload != lastUpload

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
        val library = app.libraryStore.current()
        val subscribed = library.subscribed
        var failed = false
        val found = mutableListOf<NewChapter>()

        // One request covers up to 100 series. Only series with a new upload since the last look need their own feed read.
        val uploads = try {
            app.repository.latestUploads(subscribed.map { it.id })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = isWorthRetrying(e)
            null
        }
        // Some groups schedule a chapter to go public after its upload. Every few hours each feed is read anyway, so those still notify.
        val now = System.currentTimeMillis()
        val fullCheck = now - library.fullCheckAt >= FULL_CHECK_MS
        val known = HashMap<String, Pair<String, String>>()
        val marks = HashMap<String, String>()
        for (series in subscribed) {
            val newestUpload = uploads?.get(series.id)
            val lastUpload = if (fullCheck) null else library.uploadMarks[series.id]
            if (!needsFeedCheck(series, lastUpload, newestUpload)) {
                marks[series.id] = newestUpload!!
                continue
            }
            val latest = try {
                app.repository.latestChapter(series.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A series MangaDex removed answers 404 on every run, so only a passing failure asks for a retry.
                if (isWorthRetrying(e)) failed = true
                continue
            }
            if (newestUpload != null) marks[series.id] = newestUpload
            val knownId = series.knownChapterId
            if (latest != null && knownId != null && latest.id != knownId && library.notificationsEnabled && series.notify) {
                found += NewChapter(series, latest.id, latest.number)
            }
            // First sighting only records the chapter, so old chapters never notify. With
            // notifications off the chapter is still recorded, so turning them on stays quiet.
            if (latest != null && (latest.id != knownId || series.knownChapterNumber == null)) {
                known[series.id] = latest.id to latest.number
            }
            delay(300) // stay well under MangaDex's request limit
        }
        // Everything found is written at once, so open screens redraw once per check, not once per series.
        // When the upload lookup failed, the marks from the last check stay as they were.
        app.libraryStore.recordChecks(known, marks.takeIf { uploads != null }, fullCheckAt = now.takeIf { fullCheck && uploads != null })
        if (library.notificationsEnabled) {
            for (author in library.followedAuthors) {
                val newest = try {
                    app.repository.browse(order = Order.Newest, authorId = author.id, limit = 10)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isWorthRetrying(e)) failed = true
                    continue
                }
                val fresh = newest.filter { it.id !in author.knownIds }
                if (fresh.isNotEmpty()) {
                    if (author.knownIds.isNotEmpty()) postAuthor(author.name, fresh.first())
                    app.libraryStore.markAuthorSeen(author.id, fresh.map { it.id })
                }
                delay(300)
            }
            // Saved searches you asked to hear about: a series that newly matches notifies once.
            for (search in library.savedSearches.filter { it.notify }) {
                val newest = try {
                    app.repository.browse(title = search.title, tag = search.tag, order = Order.Newest, filters = search.filters, limit = 10)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isWorthRetrying(e)) failed = true
                    continue
                }
                val fresh = newest.filter { it.id !in search.knownIds }
                if (fresh.isNotEmpty()) {
                    postSearchMatch(search.name, fresh.first(), fresh.size)
                    app.libraryStore.markSearchSeen(search.name, fresh.map { it.id })
                }
                delay(300)
            }
        }
        post(found, settings.notificationDigest)
        return if (failed) Result.retry() else Result.success()
    }

    /** The notification manager with the channel in place, or null when you have not allowed notifications. */
    private fun notifier(): NotificationManager? {
        val context = applicationContext
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return null
        return context.getSystemService(NotificationManager::class.java).also {
            it.createNotificationChannel(NotificationChannel(CHANNEL_ID, "New chapters", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    /** A notification that opens the app, with [extras] saying where. */
    private fun opensApp(requestCode: Int, extras: Intent.() -> Unit): PendingIntent = PendingIntent.getActivity(
        applicationContext,
        requestCode,
        Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            extras()
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun newBuilder(title: String): Notification.Builder = Notification.Builder(applicationContext, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_more)
        .setContentTitle(title)
        .setAutoCancel(true)

    private fun postAuthor(authorName: String, series: SeriesSummary) {
        val manager = notifier() ?: return
        manager.notify(
            series.id.hashCode(),
            newBuilder("New series by $authorName")
                .setContentText(series.title)
                .setContentIntent(opensApp(series.id.hashCode()) { putExtra(EXTRA_SERIES_ID, series.id) })
                .build(),
        )
    }

    private fun postSearchMatch(searchName: String, series: SeriesSummary, count: Int) {
        val manager = notifier() ?: return
        val id = ("search:$searchName").hashCode()
        manager.notify(
            id,
            newBuilder(if (count == 1) "New match for $searchName" else "$count new matches for $searchName")
                .setContentText(series.title)
                .setContentIntent(opensApp(id) { putExtra(EXTRA_SERIES_ID, series.id) })
                .build(),
        )
    }

    private fun post(found: List<NewChapter>, digest: Boolean) {
        if (found.isEmpty()) return
        val manager = notifier() ?: return
        if (digest) {
            val lines = digestLines(found.map { it.series.title to it.number })
            manager.notify(
                DIGEST_ID,
                newBuilder(digestTitle(found.size))
                    .setContentText(lines.first())
                    .setStyle(Notification.InboxStyle().also { style -> lines.forEach(style::addLine) })
                    .setContentIntent(opensApp(DIGEST_ID) { putExtra(EXTRA_ROUTE, "library") })
                    .build(),
            )
            return
        }
        found.forEach { item -> manager.notify(item.series.id.hashCode(), single(item)) }
        if (found.size > 1) {
            manager.notify(DIGEST_ID, newBuilder(digestTitle(found.size)).setGroup(GROUP_KEY).setGroupSummary(true).build())
        }
    }

    private fun single(item: NewChapter): Notification {
        val series = item.series
        val markRead = PendingIntent.getBroadcast(
            applicationContext,
            series.id.hashCode(),
            Intent(applicationContext, MarkReadReceiver::class.java).apply {
                putExtra(EXTRA_SERIES_ID, series.id)
                putExtra(EXTRA_CHAPTER_ID, item.chapterId)
                putExtra(EXTRA_CHAPTER_NUMBER, item.number)
                putExtra(EXTRA_TITLE, series.title)
                putExtra(EXTRA_COVER, series.coverUrl)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return newBuilder(series.title)
            .setContentText("Chapter ${item.number} is out")
            .setContentIntent(
                opensApp(series.id.hashCode()) {
                    putExtra(EXTRA_SERIES_ID, series.id)
                    putExtra(EXTRA_CHAPTER_ID, item.chapterId)
                },
            )
            .setGroup(GROUP_KEY)
            .addAction(Notification.Action.Builder(null, "Mark read", markRead).build())
            .build()
    }

    companion object {
        /** Runs every 30 minutes on any network, unless the battery is low. Safe to call on every launch. */
        fun schedule(context: Context) {
            // Create the channel up front, so its system settings page exists before the first notification.
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL_ID, "New chapters", NotificationManager.IMPORTANCE_DEFAULT))
            val request = PeriodicWorkRequestBuilder<NewChaptersWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            // UPDATE keeps the schedule and applies new constraints to a check that already exists.
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
