package com.dexter.data

import android.content.Context
import com.dexter.data.db.AppDatabase
import com.dexter.data.db.DownloadEntity
import com.dexter.data.db.QueuedDownloadEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resumeWithException

private const val PAGE_TRIES = 3

private const val PAGES_AT_ONCE = 3

/** The file name for page [index] of [total], padded so a directory listing sorts in reading order. */
fun pageFileName(index: Int, total: Int, url: String): String {
    val width = maxOf(3, total.toString().length)
    val extension = url.substringBefore('?').substringAfterLast('.', "img").takeIf { it.length in 2..4 } ?: "img"
    return index.toString().padStart(width, '0') + "." + extension
}

/**
 * The chapters to save for a "next N unread" request. [chapters] run newest first, as the series page
 * lists them. Chapters after [lastReadNumber] that are not saved yet come back oldest first, at most
 * [count] of them (all when null). Chapters that only link out cannot be saved.
 */
fun chaptersToDownload(
    chapters: List<Chapter>,
    lastReadNumber: String?,
    savedIds: Set<String>,
    count: Int?,
): List<Chapter> {
    val last = lastReadNumber?.toDoubleOrNull()
    val unread = chapters.asReversed().filter { chapter ->
        chapter.externalUrl == null && chapter.id !in savedIds &&
            (last == null || (chapter.number.toDoubleOrNull() ?: Double.MAX_VALUE) > last)
    }
    return if (count == null) unread else unread.take(count)
}

/** Turns saved rows back into chapters, oldest first, for reading a series with no connection. */
fun savedChapters(rows: List<DownloadEntity>): List<Chapter> = rows
    .sortedWith(compareBy({ it.number.toDoubleOrNull() ?: Double.MAX_VALUE }, { it.savedAt }))
    .map { Chapter(it.chapterId, it.number, it.title, it.publishedAt, group = it.groupName, volume = it.volume) }

/**
 * The chapters to delete so the saved total fits in [capBytes], oldest saved first. The chapter in [keep]
 * is never picked. Nothing is picked when the total already fits.
 */
fun chaptersOverCap(rows: List<DownloadEntity>, capBytes: Long, keep: String?): List<String> {
    var total = rows.sumOf { it.bytes }
    if (total <= capBytes) return emptyList()
    val drop = mutableListOf<String>()
    for (row in rows.sortedBy { it.savedAt }) {
        if (total <= capBytes) break
        if (row.chapterId == keep) continue
        drop += row.chapterId
        total -= row.bytes
    }
    return drop
}

/** Chapters saved on the device: page files on disk and a row per finished chapter. */
class DownloadStore(context: Context, private val db: AppDatabase, private val client: OkHttpClient) {
    private val cbz = CbzExport(context)

    private val dao get() = db.downloads()
    private val queueDao get() = db.queue()
    private val root = File(context.filesDir, "downloads")

    val saved: Flow<List<DownloadEntity>> get() = dao.observe()

    private val _active = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** The chapter being saved now, with its progress from 0 to 1. */
    val active: StateFlow<Map<String, Float>> = _active

    /** Chapters waiting to be saved, in the order they will be saved. Kept in the database, so a restart keeps them. */
    val queue: Flow<List<QueuedDownloadEntity>> get() = queueDao.observe()

    /** The jobs saving a chapter right now, by chapter id, so cancelling one stops it mid-chapter. */
    private val running = ConcurrentHashMap<String, Job>()

    /** Chapters cancelled while they were being saved. The worker reads this to tell a cancel from a stop. */
    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    /** Puts [chapter] at the end of the queue. A chapter already waiting keeps its place. */
    suspend fun enqueue(seriesId: String, seriesTitle: String, coverUrl: String?, chapter: Chapter) {
        queueDao.insert(
            QueuedDownloadEntity(
                chapterId = chapter.id,
                seriesId = seriesId,
                seriesTitle = seriesTitle,
                coverUrl = coverUrl,
                number = chapter.number,
                title = chapter.title,
                volume = chapter.volume,
                groupName = chapter.group,
                publishedAt = chapter.publishedAt,
                position = queueDao.maxPosition() + 1,
            ),
        )
    }

    /** The next chapter to save, or null when the queue is empty. */
    suspend fun nextQueued(): QueuedDownloadEntity? = queueDao.first()

    /** Takes a finished or abandoned chapter out of the queue. */
    suspend fun dequeue(chapterId: String) = queueDao.delete(chapterId)

    suspend fun bumpAttempts(chapterId: String) = queueDao.bumpAttempts(chapterId)

    /** Takes [chapterId] out of the queue, and stops it if it is being saved right now. */
    suspend fun cancel(chapterId: String) {
        queueDao.delete(chapterId)
        running[chapterId]?.let { job ->
            cancelled += chapterId
            job.cancel()
        }
    }

    /** Empties the queue and stops the chapter being saved. */
    suspend fun cancelAll() {
        queueDao.deleteAll()
        running.forEach { (id, job) ->
            cancelled += id
            job.cancel()
        }
    }

    /** Moves [chapterId] to the front of the queue, so it is saved next. */
    suspend fun moveToTop(chapterId: String) = queueDao.setPosition(chapterId, queueDao.minPosition() - 1)

    /** True once, when [chapterId] stopped because you cancelled it. */
    fun consumeCancelled(chapterId: String): Boolean = cancelled.remove(chapterId)

    /**
     * Deletes the oldest saved chapters until the total is at most [capBytes]. The chapter in [keep], just
     * saved, always stays. A cap of 0 or less means no limit.
     */
    suspend fun enforceCap(capBytes: Long, keep: String?) = withContext(Dispatchers.IO) {
        if (capBytes <= 0) return@withContext
        val rows = dao.observe().first()
        chaptersOverCap(rows, capBytes, keep).forEach { delete(it) }
    }

    /** The page files of a saved chapter as file addresses, or null when the chapter is not saved. */
    suspend fun pagesOf(chapterId: String): List<String>? = withContext(Dispatchers.IO) {
        val row = dao.get(chapterId) ?: return@withContext null
        val files = File(root, chapterId).listFiles()?.sortedBy { it.name }.orEmpty()
        if (files.size != row.pageCount) null else files.map { it.toURI().toString() }
    }

    /** Drops rows whose page files are gone, such as after a restore from Android's cloud backup. */
    suspend fun prune() = withContext(Dispatchers.IO) {
        dao.observe().first().forEach { row ->
            if (File(root, row.chapterId).listFiles()?.size != row.pageCount) dao.delete(row.chapterId)
        }
    }

    suspend fun chaptersOf(seriesId: String): List<Chapter> = savedChapters(dao.forSeries(seriesId))

    suspend fun isSaved(chapterId: String) = dao.get(chapterId) != null

    /** Exports saved chapters as CBZ files in Downloads/Dexter. Returns how many were written. */
    suspend fun exportCbz(chapterIds: List<String>): Int = chapterIds.count { id ->
        val row = dao.get(id) ?: return@count false
        cbz.export(row, File(root, id))
    }

    /** Downloads every page of [chapter]. Throws when a page still fails after retries, leaving nothing saved. */
    suspend fun save(
        seriesId: String,
        seriesTitle: String,
        coverUrl: String?,
        chapter: Chapter,
        urls: List<String>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        withContext(Dispatchers.IO) {
            running[chapter.id] = coroutineContext.job
            val dir = File(root, chapter.id).also { it.deleteRecursively(); it.mkdirs() }
            try {
                // A few pages at a time. A page that fails cancels the rest, and the whole chapter fails with it.
                val permits = Semaphore(PAGES_AT_ONCE)
                val done = AtomicInteger()
                val bytes = coroutineScope {
                    urls.mapIndexed { index, url ->
                        async {
                            permits.withPermit {
                                fetchTo(url, File(dir, pageFileName(index, urls.size, url))).also {
                                    val count = done.incrementAndGet()
                                    _active.update { it + (chapter.id to count.toFloat() / urls.size) }
                                    onProgress(count, urls.size)
                                }
                            }
                        }
                    }.awaitAll().sum()
                }
                dao.insert(
                    DownloadEntity(
                        chapterId = chapter.id,
                        seriesId = seriesId,
                        seriesTitle = seriesTitle,
                        coverUrl = coverUrl,
                        number = chapter.number,
                        title = chapter.title,
                        volume = chapter.volume,
                        groupName = chapter.group,
                        publishedAt = chapter.publishedAt,
                        pageCount = urls.size,
                        bytes = bytes,
                        savedAt = System.currentTimeMillis(),
                    ),
                )
            } catch (e: Exception) {
                dir.deleteRecursively()
                throw e
            } finally {
                running.remove(chapter.id)
                _active.update { it - chapter.id }
            }
        }
    }

    suspend fun delete(chapterId: String) = withContext(Dispatchers.IO) {
        dao.delete(chapterId)
        File(root, chapterId).deleteRecursively()
        Unit
    }

    suspend fun deleteSeries(seriesId: String) = withContext(Dispatchers.IO) {
        dao.forSeries(seriesId).forEach { File(root, it.chapterId).deleteRecursively() }
        dao.deleteSeries(seriesId)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
        root.deleteRecursively()
        Unit
    }

    private suspend fun fetchTo(url: String, target: File): Long {
        var failure: IOException? = null
        repeat(PAGE_TRIES) {
            try {
                return fetchOnce(url, target)
            } catch (e: IOException) {
                failure = e
            }
        }
        throw failure ?: IOException("Page failed")
    }

    /**
     * One try at one page. The file is written on OkHttp's thread as the bytes arrive. Cancelling the
     * coroutine cancels the call, which stops a page mid-download instead of waiting for it to finish.
     */
    private suspend fun fetchOnce(url: String, target: File): Long = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resumeWith(runCatching { response.use { writePage(it, target) } })
                }
            },
        )
    }

    private fun writePage(response: Response, target: File): Long {
        if (!response.isSuccessful) throw IOException("Page failed: ${response.code}")
        val temp = File(target.parentFile, target.name + ".part")
        response.body.byteStream().use { input -> temp.outputStream().use { input.copyTo(it) } }
        if (!temp.renameTo(target)) throw IOException("Could not save page")
        return target.length()
    }
}
