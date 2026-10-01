package com.dexter.data

import android.content.Context
import com.dexter.data.db.AppDatabase
import com.dexter.data.db.DownloadEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
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

/** Chapters saved on the device: page files on disk and a row per finished chapter. */
class DownloadStore(context: Context, private val db: AppDatabase, private val client: OkHttpClient) {
    private val dao get() = db.downloads()
    private val root = File(context.filesDir, "downloads")

    val saved: Flow<List<DownloadEntity>> get() = dao.observe()

    private val _active = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** Chapters being saved now, each with its progress from 0 to 1. */
    val active: StateFlow<Map<String, Float>> = _active

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

    /** Downloads every page of [chapter]. Throws when a page still fails after retries, leaving nothing saved. */
    suspend fun save(seriesId: String, seriesTitle: String, coverUrl: String?, chapter: Chapter, urls: List<String>) {
        withContext(Dispatchers.IO) {
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
                                    _active.update { it + (chapter.id to done.incrementAndGet().toFloat() / urls.size) }
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
                _active.update { it - chapter.id }
            }
        }
    }

    /** Marks a chapter as waiting, so the series page shows it before the worker starts. */
    fun markQueued(chapterId: String) = _active.update { if (chapterId in it) it else it + (chapterId to 0f) }

    fun clearQueued(chapterId: String) = _active.update { it - chapterId }

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
