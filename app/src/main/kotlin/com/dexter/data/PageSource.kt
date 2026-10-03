package com.dexter.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * Where the reader's pages come from. The reader used to know only MangaDex chapters; local
 * archives (a scanned comics folder, a bare CBZ from cloud/NAS cache) now flow through the same
 * screen, and this is the switch the ViewModel branches on.
 */
sealed interface PageSource {
    /**
     * A MangaDex chapter. Page addresses stay on the repository/downloads path; [resolvePages]
     * deliberately does not handle this variant (see its KDoc).
     */
    data class MangaDex(val chapterId: String) : PageSource

    /** One chapter of a locally scanned series: a folder of images or a single CBZ/ZIP archive. */
    data class Local(val localChapter: LocalChapter) : PageSource

    /**
     * A bare archive file already on disk, such as a cloud or NAS archive downloaded to the cache
     * by [CloudFileProvider.cachedArchive]. It reads as a one-chapter series of its own.
     */
    data class Archive(val file: File) : PageSource
}

/**
 * True for [PageSource.Local] and [PageSource.Archive]: files on this device. The ViewModel uses
 * this to skip everything networked: MangaDex read markers, tracker progress, auto-download of the
 * next chapter, page-address renewal, and next-chapter prefetch.
 */
fun PageSource.isLocal(): Boolean = this is PageSource.Local || this is PageSource.Archive

/**
 * The page addresses for [source], in reading order. Local content resolves to `file://` URI
 * strings: Coil 3 loads those natively, and [ImageExport.bytes] has an explicit `file:` branch, so
 * saving, sharing, and copying pages keep working without touching the export path.
 *
 * - [PageSource.Local]: folder pages address their files directly, nothing is copied. Archive
 *   entries are extracted once per chapter under `cacheDir/local_pages/<chapter>/`, and repeat
 *   opens reuse the extracted files.
 * - [PageSource.Archive]: the same extraction approach, as a one-chapter series.
 * - [PageSource.MangaDex]: not resolved here by design; the ViewModel keeps using
 *   `downloads.pagesOf` / `repository.pages` for it. Returns [emptyList] so a missed branch fails
 *   as "no pages" rather than crashing; callers must branch on [isLocal] first.
 *
 * All IO runs on [Dispatchers.IO]. Never throws: any failure returns [emptyList] and the caller
 * shows the error.
 */
suspend fun resolvePages(source: PageSource, cacheDir: File): List<String> =
    withContext(Dispatchers.IO) {
        try {
            when (source) {
                is PageSource.MangaDex -> emptyList()
                is PageSource.Local -> localPages(source.localChapter, cacheDir)
                is PageSource.Archive -> localPages(source.file.toLocalChapter(), cacheDir)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

/** The pages of a local chapter as `file://` address strings, in reading order. */
private fun localPages(chapter: LocalChapter, cacheDir: File): List<String> {
    val pages = localChapterPages(chapter)
    if (pages.isEmpty()) return emptyList()
    val dirName = "local_${chapter.id.sanitizeFileName()}"
    return pages.mapIndexedNotNull { index, page ->
        when (page) {
            is LocalPage.FilePage -> page.file.toAddress()
            is LocalPage.ArchivePage -> {
                // Strip any folders inside the archive name; the index keeps the order stable.
                val name = "${index.toString().padStart(4, '0')}_${page.entryName.substringAfterLast('/').sanitizeFileName()}"
                extractPageToCache(page.archive, page.entryName, cacheDir, dirName, name)?.toAddress()
            }
        }
    }
}

/**
 * Extracts one archive entry into the per-chapter page cache, reusing the file when a previous
 * open already extracted it. Mirrors [extractToCache] but keeps each chapter's pages in their own
 * directory so one archive per series folder cannot collide with another's.
 */
private fun extractPageToCache(archive: File, entryName: String, cacheDir: File, dirName: String, name: String): File? {
    return try {
        val dir = File(cacheDir, "local_pages/$dirName").also { it.mkdirs() }
        val out = dir.resolve(name)
        if (out.isFile && out.length() > 0) return out
        ZipFile(archive).use { zip ->
            val entry = zip.getEntry(entryName) ?: return null
            zip.getInputStream(entry).use { input -> out.writeAtomically { input.copyTo(it) } }
        }
        out
    } catch (e: Exception) {
        null
    }
}

/** A bare archive file as a one-chapter [LocalChapter], so it flows through the local page path. */
fun File.toLocalChapter(): LocalChapter = LocalChapter(
    id = "archive:${absolutePath.hashCode().toString(16)}",
    seriesId = "archive",
    title = nameWithoutExtension,
    order = 0,
    source = this,
)

/** The reader's [Chapter] for a local chapter: the file or archive name is its number. */
fun LocalChapter.toReaderChapter(): Chapter = Chapter(
    id = id,
    number = title,
    title = "",
    publishedAt = "",
)

/**
 * Finds the [LocalChapter] behind a reader chapter id (`local:<hash>:ch<index>` or
 * `local:<hash>:loose`, see [localChapters]) by scanning [roots]. Returns null when the id is not a
 * local chapter id, no root holds its series, or the scan fails. The app keeps a single local root
 * in `QolPrefs.localFolder`; callers pass `listOfNotNull(folder?.let(::File))`.
 */
suspend fun localChapterById(chapterId: String, roots: List<File>): LocalChapter? =
    withContext(Dispatchers.IO) {
        val seriesId = chapterId.substringBeforeLast(':')
        if (seriesId == chapterId || !seriesId.startsWith("local:")) return@withContext null
        roots.asSequence()
            .filter { it.isDirectory }
            .flatMap { root -> runCatching { scanLocalRoot(root) }.getOrDefault(emptyList()) }
            .firstOrNull { it.id == seriesId }
            ?.let { series ->
                runCatching { localChapters(series.dir) }.getOrDefault(emptyList())
                    .firstOrNull { it.id == chapterId }
            }
    }

/** Convenience overload for the single-root case. */
suspend fun localChapterById(chapterId: String, root: File?): LocalChapter? =
    localChapterById(chapterId, listOfNotNull(root))

/** `file:///…` address string for a page file. See [resolvePages] for why this form is used. */
private fun File.toAddress(): String = Uri.fromFile(this).toString()

/**
 * Makes a string safe as one file or directory name. The hex hash suffix keeps distinct inputs
 * distinct after the cleanup shortens them.
 */
private fun String.sanitizeFileName(): String {
    val clean = map { c -> if (c.isLetterOrDigit() || c == '-' || c == '_' || c == '.') c else '_' }
        .joinToString("").take(48)
    return "${clean}_${hashCode().toString(16)}"
}
