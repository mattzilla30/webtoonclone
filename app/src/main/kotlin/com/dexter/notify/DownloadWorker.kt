package com.dexter.notify

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dexter.data.Chapter
import com.dexter.data.DownloadStore
import com.dexter.data.MangaDexRepository
import com.dexter.data.isWorthRetrying
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

private const val KEY_SERIES = "series"
private const val KEY_SERIES_TITLE = "seriesTitle"
private const val KEY_COVER = "cover"
private const val KEY_CHAPTER = "chapter"
private const val KEY_NUMBER = "number"
private const val KEY_TITLE = "title"
private const val KEY_VOLUME = "volume"
private const val KEY_GROUP = "group"
private const val KEY_PUBLISHED = "published"
private const val MAX_ATTEMPTS = 4
private const val QUEUE = "downloads"

/**
 * Saves one chapter's pages on the device. WorkManager retries it when the network drops. Chapters wait
 * in one queue and save one at a time, which keeps a "download all" under MangaDex's request limits.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val repository: MangaDexRepository by inject()
    private val store: DownloadStore by inject()

    override suspend fun doWork(): Result {
        val data = inputData
        // A failed job would fail every chapter queued after it, so a job that cannot run ends as done.
        val chapter = Chapter(
            id = data.getString(KEY_CHAPTER) ?: return Result.success(),
            number = data.getString(KEY_NUMBER).orEmpty(),
            title = data.getString(KEY_TITLE).orEmpty(),
            publishedAt = data.getString(KEY_PUBLISHED).orEmpty(),
            group = data.getString(KEY_GROUP),
            volume = data.getString(KEY_VOLUME),
        )
        val seriesId = data.getString(KEY_SERIES) ?: return Result.success()
        // The same chapter can be queued twice if the app restarted in between. The second one has nothing to do.
        if (store.isSaved(chapter.id)) {
            store.clearQueued(chapter.id)
            return Result.success()
        }
        return try {
            val urls = repository.pages(chapter.id, forceRefresh = true)
            store.save(
                seriesId = seriesId,
                seriesTitle = data.getString(KEY_SERIES_TITLE).orEmpty(),
                coverUrl = data.getString(KEY_COVER),
                chapter = chapter,
                urls = urls,
            )
            Result.success()
        } catch (e: CancellationException) {
            store.clearQueued(chapter.id)
            throw e
        } catch (e: Exception) {
            // After the last try the chapter is given up on, and the queue moves on to the next one.
            if (runAttemptCount >= MAX_ATTEMPTS || !isWorthRetrying(e)) {
                store.clearQueued(chapter.id)
                Result.success()
            } else {
                // Still waiting for its next try, so the series page keeps showing it as queued.
                store.markQueued(chapter.id)
                Result.retry()
            }
        }
    }

    companion object {
        /** Queues [chapter] for saving behind any chapters already waiting. A chapter already waiting or saving is not queued again. */
        fun enqueue(context: Context, store: DownloadStore, seriesId: String, seriesTitle: String, coverUrl: String?, chapter: Chapter, wifiOnly: Boolean) {
            if (chapter.id in store.active.value) return
            store.markQueued(chapter.id)
            val input = Data.Builder()
                .putString(KEY_SERIES, seriesId)
                .putString(KEY_SERIES_TITLE, seriesTitle)
                .putString(KEY_COVER, coverUrl)
                .putString(KEY_CHAPTER, chapter.id)
                .putString(KEY_NUMBER, chapter.number)
                .putString(KEY_TITLE, chapter.title)
                .putString(KEY_VOLUME, chapter.volume)
                .putString(KEY_GROUP, chapter.group)
                .putString(KEY_PUBLISHED, chapter.publishedAt)
                .build()
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(input)
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
