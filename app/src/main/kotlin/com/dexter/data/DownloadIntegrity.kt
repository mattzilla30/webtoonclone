package com.dexter.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** How one downloaded chapter page failed verification. */
enum class PageProblem {
    /** The page file is missing from the chapter folder. */
    MISSING,

    /** The page file exists but is empty or too small to be an image. */
    EMPTY,

    /** The page bytes do not match the hash recorded when it downloaded. */
    HASH_MISMATCH,
}

/** One bad page inside a downloaded chapter. [index] counts from 0 in reading order. */
data class BadPage(
    val index: Int,
    val fileName: String,
    val problem: PageProblem,
    val expectedBytes: Long = 0,
    val actualBytes: Long = 0,
)

/** The integrity verdict for one downloaded chapter. */
data class ChapterIntegrity(
    val chapterId: String,
    val expectedPages: Int,
    val badPages: List<BadPage> = emptyList(),
) {
    val ok: Boolean get() = badPages.isEmpty()
}

/**
 * Verifies downloaded chapters and repairs them. Verification compares the page files on disk
 * against the chapter's page count and, when hashes were recorded at download time via
 * [recordPageHashes], against SHA-256 digests. Repair re-downloads just the bad pages: unlike the
 * normal downloader, which replaces the whole chapter folder, repair keeps every good page and
 * fetches only what failed.
 */
class DownloadIntegrity(
    private val store: DownloadStore,
    private val client: OkHttpClient,
) {
    /**
     * Checks one chapter. [expectedPages] is the page count the download row recorded; pass null
     * to check only presence and non-emptiness. [expectedHashes] maps page file names to the
     * SHA-256 hex recorded at download time; pass null to skip hash checks.
     */
    suspend fun verifyChapter(
        chapterId: String,
        expectedPages: Int? = null,
        expectedHashes: Map<String, String>? = null,
    ): ChapterIntegrity = withContext(Dispatchers.IO) {
        val files = store.pageFiles(chapterId).associateBy { it.name }
        val count = expectedPages ?: files.size
        val bad = mutableListOf<BadPage>()
        val width = maxOf(3, count.toString().length)
        for (index in 0 until count) {
            // Page files sort by padded name; match the file starting with the padded index.
            val prefix = index.toString().padStart(width, '0')
            val file = files.keys.firstOrNull { it.startsWith(prefix) }?.let { files.getValue(it) }
            if (file == null || !file.exists()) {
                bad += BadPage(index, "$prefix.*", PageProblem.MISSING)
                continue
            }
            if (file.length() < MIN_IMAGE_BYTES) {
                bad += BadPage(index, file.name, PageProblem.EMPTY, actualBytes = file.length())
                continue
            }
            val expected = expectedHashes?.get(file.name)
            if (expected != null && sha256Hex(file) != expected.lowercase()) {
                bad += BadPage(index, file.name, PageProblem.HASH_MISMATCH, actualBytes = file.length())
            }
        }
        ChapterIntegrity(chapterId, count, bad)
    }

    /** Checks every downloaded chapter of a series, returning only the ones with problems. */
    suspend fun verifySeries(
        seriesId: String,
        hashes: Map<String, Map<String, String>> = emptyMap(),
    ): List<ChapterIntegrity> = withContext(Dispatchers.IO) {
        store.chaptersOf(seriesId).mapNotNull { chapter ->
            // Prefer caller-supplied hashes; otherwise use the ones recorded at download time.
            val expected = hashes[chapter.id] ?: store.loadPageHashes(chapter.id)
            verifyChapter(chapter.id, null, expected).takeUnless { it.ok }
        }
    }

    /**
     * Repairs a chapter: deletes the bad page files and re-downloads just those pages, keeping
     * every good one. [pageUrls] are the reader's page URLs in order (see
     * `MangaDexRepository.pages`). Returns the fresh verification, so the caller can show what is
     * still broken.
     */
    suspend fun repairChapter(
        chapterId: String,
        report: ChapterIntegrity,
        pageUrls: List<String>,
    ): ChapterIntegrity = withContext(Dispatchers.IO) {
        require(pageUrls.size == report.expectedPages) {
            "Page URLs (${pageUrls.size}) do not match the expected page count (${report.expectedPages})"
        }
        val dir = store.dirFor(report.chapterId)
        report.badPages.forEach { bad ->
            dir.listFiles { file -> file.name == bad.fileName || file.name.startsWith(bad.fileName.removeSuffix(".*")) }
                .orEmpty().forEach { it.delete() }
        }
        coroutineScope {
            report.badPages.map { bad ->
                async {
                    val url = pageUrls[bad.index]
                    val out = File(dir, pageFileName(bad.index, pageUrls.size, url))
                    fetchTo(url, out)
                }
            }.awaitAll()
        }
        // The repaired pages have new bytes: re-record hashes so later checks use them.
        runCatching { store.savePageHashes(report.chapterId) }
        verifyChapter(report.chapterId, report.expectedPages, store.loadPageHashes(report.chapterId))
    }

    private fun fetchTo(url: String, out: File) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Repair download failed: ${response.code}")
            val body = response.body ?: throw IOException("Empty repair response")
            out.writeAtomically { body.byteStream().copyTo(it) }
        }
        if (out.length() < MIN_IMAGE_BYTES) {
            out.delete()
            throw IOException("Repair downloaded an empty page")
        }
    }

    companion object {
        /** Files smaller than this are never valid images; catches truncated downloads. */
        const val MIN_IMAGE_BYTES = 512L

        /** Records SHA-256 digests of [files] for later [verifyChapter] checks. */
        fun recordPageHashes(files: List<File>): Map<String, String> =
            files.associate { it.name to sha256Hex(it) }

        private fun sha256Hex(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read = input.read(buffer)
                while (read >= 0) {
                    digest.update(buffer, 0, read)
                    read = input.read(buffer)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
