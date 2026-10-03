package com.dexter.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.dexter.DexterApp
import com.dexter.MainActivity
import com.dexter.data.Chapter
import com.dexter.data.Order
import com.dexter.data.SavedSeries
import com.dexter.data.SeriesSummary
import com.dexter.data.Settings
import com.dexter.data.HttpStatusException
import com.dexter.data.UpdateCheckStore
import com.dexter.data.isMuted
import com.dexter.data.isWorthRetrying
import com.dexter.data.seriesUpdateDue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** The notification channel for new chapters. Settings links to its system page. */
const val CHANNEL_ID = "new_chapters"
private const val WORK_NAME = "new-chapters"
private const val NOW_WORK_NAME = "new-chapters-now"
private const val COVER_PX = 256
private const val GROUP_KEY = "new_chapters_group"
private const val DIGEST_ID = 1
private const val FULL_CHECK_MS = 6L * 60 * 60 * 1000
/** A series MangaDex no longer knows (404) is not looked up again for a week. */
private const val GONE_SERIES_BACKOFF_MS = 7L * 24 * 60 * 60 * 1000
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
        val subscribedIds = subscribed.mapTo(HashSet()) { it.id }
        // A series with its own check interval only gets its feed read when that interval has passed.
        val updateChecks = UpdateCheckStore(applicationContext)
        val checkTimes = updateChecks.all()
        val due = subscribed.filter { seriesUpdateDue(it.id, checkTimes[it.id], settings) }
        val dueIds = due.mapTo(HashSet()) { it.id }
        var failed = false
        val found = mutableListOf<NewChapter>()
        val downloadable = mutableListOf<NewChapter>()

        // One request covers up to 100 series. Only series with a new upload since the last look need their own feed read.
        val uploads = try {
            app.repository.latestUploads(due.map { it.id })
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
        val gone = HashSet<String>()
        for (series in due) {
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
                if (e is HttpStatusException && e.code == 404) {
                    // Remember the removal with a long backoff instead of re-fetching it every cycle.
                    gone += series.id
                } else if (isWorthRetrying(e)) {
                    failed = true
                }
                continue
            }
            if (newestUpload != null) marks[series.id] = newestUpload
            val knownId = series.knownChapterId
            if (latest != null && knownId != null && latest.id != knownId) {
                // A blacklisted chapter is invisible everywhere: no notification, no auto-download.
                val blacklisted = app.downloadStore.isBlacklisted(series.id, latest.id)
                if (library.notificationsEnabled && series.notify && !isMuted(series.id, library, settings) && !blacklisted) {
                    found += NewChapter(series, latest.id, latest.number)
                }
                // Auto-download does not need notifications on, but it skips muted series like they do.
                if (settings.autoDownloadNew && !isMuted(series.id, library, settings) && !blacklisted) {
                    downloadable += NewChapter(series, latest.id, latest.number)
                }
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
        // Series whose own interval has not passed keep their marks, so they are not read again next
        // run; marks of series no longer subscribed still drop out.
        val kept = library.uploadMarks.filterKeys { id -> id !in dueIds && id in subscribedIds }
        app.libraryStore.recordChecks(known, (kept + marks).takeIf { uploads != null }, fullCheckAt = now.takeIf { fullCheck && uploads != null })
        // On a failed run the retry re-checks everything, so timestamps are only kept when it passed.
        // A removed series is stamped with a long backoff instead of the normal interval.
        if (!failed) for (series in due) {
            updateChecks.markChecked(series.id, if (series.id in gone) now + GONE_SERIES_BACKOFF_MS else now)
        }
        val autoSaved = autoDownloadNew(downloadable, settings)
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
        post(found, settings.notificationDigest, autoSaved)
        app.libraryStore.markChecked(System.currentTimeMillis())
        return if (failed) Result.retry() else Result.success()
    }

    /**
     * Queues newly found chapters for saving. The download worker's constraints honor [Settings.downloadWifiOnly],
     * and it enforces [Settings.downloadCapMb] after each chapter. Returns the chapters actually queued.
     */
    private suspend fun autoDownloadNew(items: List<NewChapter>, settings: Settings): List<NewChapter> {
        if (!settings.autoDownloadNew || items.isEmpty()) return emptyList()
        val app = applicationContext as DexterApp
        return items.filter { item ->
            // A chapter saved meanwhile has nothing left to do. The queue ignores one it already holds.
            if (app.downloadStore.isSaved(item.chapterId)) return@filter false
            if (app.downloadStore.isBlacklisted(item.series.id, item.chapterId)) return@filter false
            val chapter = Chapter(item.chapterId, item.number, "", "")
            DownloadWorker.enqueue(applicationContext, app.downloadStore, item.series.id, item.series.title, item.series.coverUrl, chapter, settings.downloadWifiOnly)
            true
        }
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

    private suspend fun post(found: List<NewChapter>, digest: Boolean, autoSaved: List<NewChapter> = emptyList()) {
        if (found.isEmpty()) return
        val manager = notifier() ?: return
        val autoIds = autoSaved.mapTo(HashSet()) { it.chapterId }
        if (digest) {
            val lines = digestLines(found.map { it.series.title to it.number }).toMutableList()
            if (autoSaved.isNotEmpty()) {
                lines += "Auto-saving ${autoSaved.size} ${if (autoSaved.size == 1) "chapter" else "chapters"} in the background."
            }
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
        found.forEach { item -> manager.notify(item.series.id.hashCode(), single(item, item.chapterId in autoIds)) }
        if (found.size > 1) {
            manager.notify(DIGEST_ID, newBuilder(digestTitle(found.size)).setGroup(GROUP_KEY).setGroupSummary(true).build())
        }
    }

    /** The series cover, small enough for a notification, or null when it does not load. */
    private suspend fun cover(url: String?): Bitmap? {
        if (url == null) return null
        val context = applicationContext
        val request = ImageRequest.Builder(context).data(url).size(COVER_PX).build()
        return (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
    }

    private suspend fun single(item: NewChapter, autoSaving: Boolean = false): Notification {
        val series = item.series
        val download = PendingIntent.getBroadcast(
            applicationContext,
            ("dl:" + series.id).hashCode(),
            Intent(applicationContext, DownloadActionReceiver::class.java).apply {
                action = ACTION_DOWNLOAD
                putExtra(EXTRA_SERIES_ID, series.id)
                putExtra(EXTRA_CHAPTER_ID, item.chapterId)
                putExtra(EXTRA_CHAPTER_NUMBER, item.number)
                putExtra(EXTRA_TITLE, series.title)
                putExtra(EXTRA_COVER, series.coverUrl)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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
        val read = opensApp(series.id.hashCode()) {
            putExtra(EXTRA_SERIES_ID, series.id)
            putExtra(EXTRA_CHAPTER_ID, item.chapterId)
        }
        return newBuilder(series.title)
            .setContentText("Chapter ${item.number} is out")
            .setSubText(if (autoSaving) "Auto-saving in the background" else null)
            .setLargeIcon(cover(series.coverUrl))
            .setContentIntent(read)
            .setGroup(GROUP_KEY)
            .addAction(Notification.Action.Builder(null, "Read now", read).build())
            .addAction(Notification.Action.Builder(null, "Download", download).build())
            .addAction(Notification.Action.Builder(null, "Mark read", markRead).build())
            .build()
    }

    companion object {
        /** Checks once now, whatever the schedule, on any network. */
        fun checkNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<NewChaptersWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        /** Runs every [minutes] on any network, unless the battery is low. Safe to call on every launch. */
        fun schedule(context: Context, minutes: Int) {
            // Create the channel up front, so its system settings page exists before the first notification.
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL_ID, "New chapters", NotificationManager.IMPORTANCE_DEFAULT))
            val request = PeriodicWorkRequestBuilder<NewChaptersWorker>(minutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
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
