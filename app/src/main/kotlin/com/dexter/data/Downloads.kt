package com.dexter.data

import android.content.Context
import com.dexter.data.db.AppDatabase
import com.dexter.data.db.DownloadEntity
import com.dexter.data.db.QueuedDownloadEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
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

/** Pages are usually a few hundred KB; 1 MB each leaves room for tall webtoon strips without refusing chapters that fit. */
private const val EXPECTED_BYTES_PER_PAGE = 1L * 1024 * 1024

/** Refuse to download below this much free space, even for a tiny chapter. */
private const val MIN_FREE_BYTES = 50L * 1024 * 1024

/** The file name for page [index] of [total], padded so a directory listing sorts in reading order. */
fun pageFileName(index: Int, total: Int, url: String): String {
    val width = maxOf(3, total.toString().length)
    val extension = url.substringBefore('?').substringAfterLast('.', "img").takeIf { it.length in 2..4 } ?: "img"
    return index.toString().padStart(width, '0') + "." + extension
}

/**
 * The page files inside [dir], in reading order. In-progress `.part` temp files from an
 * interrupted write are never pages: they must not be counted, verified, or exported.
 */
private fun pageFilesIn(dir: File): List<File> =
    dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }?.sortedBy { it.name }.orEmpty()

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
class DownloadStore(
    context: Context,
    private val db: AppDatabase,
    private val client: OkHttpClient,
    /** Blacklisted chapters are never enqueued. Defaults so existing Koin wiring keeps compiling. */
    private val blacklist: ChapterBlacklist = ChapterBlacklist(context),
) {
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

    /** Puts [chapter] at the end of the queue. A chapter already waiting keeps its place. Blacklisted chapters are refused. */
    suspend fun enqueue(seriesId: String, seriesTitle: String, coverUrl: String?, chapter: Chapter) {
        if (blacklist.isBlacklisted(seriesId, chapter.id)) return
        // A cancel from earlier no longer applies: queuing the chapter again means you want it.
        cancelled -= chapter.id
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

    /** True when the user blacklisted [chapterId] of [seriesId]: it must not download or notify. */
    suspend fun isBlacklisted(seriesId: String, chapterId: String): Boolean =
        blacklist.isBlacklisted(seriesId, chapterId)

    /** The next chapter to save, or null when the queue is empty. */
    suspend fun nextQueued(): QueuedDownloadEntity? = queueDao.first()

    /** Takes a finished or abandoned chapter out of the queue. */
    suspend fun dequeue(chapterId: String) = queueDao.delete(chapterId)

    suspend fun bumpAttempts(chapterId: String) = queueDao.bumpAttempts(chapterId)

    /** Takes [chapterId] out of the queue, and stops it if it is being saved right now. */
    suspend fun cancel(chapterId: String) {
        queueDao.delete(chapterId)
        // Always record the cancel, even when nothing is saving yet: the worker fetches the page
        // list over the network before save() registers its job, and a cancel in that window must
        // still stop the chapter instead of saving it after all.
        cancelled += chapterId
        running[chapterId]?.cancel()
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
        val files = pageFilesIn(File(root, chapterId))
        if (files.size != row.pageCount) null else files.map { it.toURI().toString() }
    }

    /** Drops rows whose page files are gone, such as after a restore from Android's cloud backup. */
    suspend fun prune() = withContext(Dispatchers.IO) {
        // Chapters being saved right now keep their in-progress files: a `.part` sweep must not
        // race a re-download and eat its pages.
        val busy = running.keys + _active.value.keys
        // Hash-recording temps live next to the chapter dirs, where the `.part` sweep inside each
        // dir can't see them; drop the stale ones so they don't accumulate.
        root.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".sha256.part") && it.name.removeSuffix(".sha256.part") !in busy }
            ?.forEach { it.delete() }
        dao.observe().first().forEach { row ->
            if (row.chapterId in busy) return@forEach
            val dir = File(root, row.chapterId)
            // Interrupted writes leave `.part` files behind. They are never pages, so sweep them
            // here instead of letting them fail the page count and drop a good chapter's row.
            dir.listFiles()?.filter { it.isFile && it.name.endsWith(".part") }?.forEach { it.delete() }
            if (pageFilesIn(dir).size != row.pageCount) dao.delete(row.chapterId)
        }
    }

    suspend fun chaptersOf(seriesId: String): List<Chapter> = savedChapters(dao.forSeries(seriesId))

    suspend fun isSaved(chapterId: String) = dao.get(chapterId) != null

    /** Ids of series with at least one saved chapter, for offline-only library views. */
    suspend fun savedSeriesIds(): Set<String> = dao.observe().first().mapTo(HashSet()) { it.seriesId }

    /**
     * Re-inserts a downloaded chapter's row from a backup. The page files must already be in place;
     * [prune] drops rows whose files are missing.
     */
    suspend fun restoreRow(row: SavedDownload) {
        dao.insert(row.toEntity())
    }

    /** The folder holding [chapterId]'s page files, for the backup archive. */
    fun dirFor(chapterId: String): File = File(root, chapterId)

    /** The downloads root, for the backup archive's containment checks. */
    internal val rootDir: File get() = root

    /**
     * Drops the recorded page hashes for [chapterId], finished sidecar and in-progress temp, e.g.
     * after a restore writes different pages. Verification then degrades to presence checks.
     */
    internal fun clearPageHashes(chapterId: String) {
        hashFile(chapterId).delete()
        File(root, "$chapterId.sha256.part").delete()
    }

    /** The page files of [chapterId], in reading order, for the backup archive. */
    suspend fun pageFiles(chapterId: String): List<File> = withContext(Dispatchers.IO) {
        pageFilesIn(dirFor(chapterId))
    }

    /** The sidecar file holding [chapterId]'s recorded page hashes. A sibling of the chapter dir, never a page. */
    private fun hashFile(chapterId: String): File = File(root, "$chapterId.sha256")

    /**
     * Records SHA-256 hashes of [chapterId]'s current page files, for later integrity checks
     * (see [DownloadIntegrity.verifyChapter]). Called once a download finishes, while the files
     * are known good; repair re-records after replacing pages.
     */
    suspend fun savePageHashes(chapterId: String) = withContext(Dispatchers.IO) {
        val hashes = DownloadIntegrity.recordPageHashes(pageFilesIn(dirFor(chapterId)))
        hashFile(chapterId).writeAtomically { out ->
            out.write(hashes.entries.joinToString("\n") { (name, hash) -> "$name:$hash" }.toByteArray())
        }
        Unit
    }

    /** The page hashes recorded by [savePageHashes], or null when none were recorded. */
    suspend fun loadPageHashes(chapterId: String): Map<String, String>? = withContext(Dispatchers.IO) {
        val file = hashFile(chapterId)
        if (!file.isFile) return@withContext null
        runCatching {
            file.readLines().mapNotNull { line ->
                val name = line.substringBefore(':').takeIf { it.isNotEmpty() }
                val hash = line.substringAfter(':', "").takeIf { it.isNotEmpty() }
                if (name != null && hash != null) name to hash else null
            }.toMap().takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

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
        // The save runs under its own SupervisorJob, parented to the caller's job. Cancelling one
        // chapter's job in cancel() then stops only the save: without this, structured concurrency
        // propagates the cancellation to the caller (DownloadWorker), whose "a cancel moves the
        // queue on" branch could never run and the whole queue would die with the chapter.
        // Stopping the worker still stops the save, because the supervisor is its child.
        val supervisor = SupervisorJob(currentCoroutineContext().job)
        try {
            withContext(Dispatchers.IO + supervisor) {
                running[chapter.id] = coroutineContext.job
                // Check before touching anything: wiping the old dir first would lose the chapter
                // for nothing when the disk can't hold the new one.
                val needed = maxOf(MIN_FREE_BYTES, urls.size * EXPECTED_BYTES_PER_PAGE)
                if (root.usableSpace < needed) throw IOException("Not enough storage space to download this chapter")
                val dir = File(root, chapter.id).also { it.deleteRecursively(); it.mkdirs() }
                // A re-download must not keep the previous download's hashes.
                clearPageHashes(chapter.id)
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
                    // Record page hashes while the files are known good, for later integrity checks.
                    // Best effort: a hashing failure must never fail an otherwise good download.
                    // A cancel landing in the hashing window must still cancel, so it is rethrown.
                    try {
                        savePageHashes(chapter.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Best effort only.
                    }
                } catch (e: Exception) {
                    dir.deleteRecursively()
                    throw e
                } finally {
                    running.remove(chapter.id)
                    _active.update { it - chapter.id }
                }
            }
        } finally {
            supervisor.cancel()
        }
    }

    suspend fun delete(chapterId: String) = withContext(Dispatchers.IO) {
        dao.delete(chapterId)
        File(root, chapterId).deleteRecursively()
        clearPageHashes(chapterId)
        Unit
    }

    /** Replaces a saved chapter's row, for metadata edits. The pages on disk are untouched. */
    suspend fun updateRow(row: DownloadEntity) = withContext(Dispatchers.IO) {
        dao.insert(row)
    }

    suspend fun row(chapterId: String): DownloadEntity? = withContext(Dispatchers.IO) {
        dao.get(chapterId)
    }

    suspend fun deleteSeries(seriesId: String) = withContext(Dispatchers.IO) {
        dao.forSeries(seriesId).forEach {
            File(root, it.chapterId).deleteRecursively()
            clearPageHashes(it.chapterId)
        }
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
        // Some CDNs omit the header; when one is there, the page must be an image, not an
        // error page or a login redirect served with a 200.
        val contentType = response.header("Content-Type")
        if (contentType != null && !contentType.startsWith("image/")) {
            throw IOException("Page is not an image: $contentType")
        }
        val temp = File(target.parentFile, target.name + ".part")
        response.body.byteStream().use { input -> temp.outputStream().use { input.copyTo(it) } }
        // A 200 with an empty body is not a page; fail it like any bad page so it retries.
        if (temp.length() == 0L) {
            temp.delete()
            throw IOException("Page is empty")
        }
        if (!temp.renameTo(target)) throw IOException("Could not save page")
        return target.length()
    }
}
