package com.dexter.data

import java.io.File
import java.util.zip.ZipFile

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp")
private val ARCHIVE_EXTENSIONS = setOf("cbz", "zip")

/**
 * A series read from the user's own files, Mihon LocalSource style. The root folder the user picks
 * holds one folder per series; each series folder holds chapter folders, CBZ/ZIP archives, or loose
 * image files. Everything scans on demand, nothing is copied.
 */
data class LocalSeries(
    val id: String,
    val title: String,
    val dir: File,
    /**
     * The best cover found: a "cover.*" file or the first page of the first chapter. For archives
     * this is an [LocalPage.ArchivePage] descriptor, which the UI extracts to the cache once via
     * [extractCoverToCache].
     */
    val cover: LocalPage?,
    val chapterCount: Int,
)

/** One chapter of a [LocalSeries]: a folder of images or a single CBZ/ZIP archive. */
data class LocalChapter(
    val id: String,
    val seriesId: String,
    val title: String,
    val order: Int,
    /** The folder or archive file holding the pages. */
    val source: File,
)

/** A local series as a library entry, so covers and progress work the same as remote series. */
fun LocalSeries.toSavedSeries(coverUrl: String? = null): SavedSeries =
    SavedSeries(
        id = id,
        title = title,
        // The UI passes the extracted cover's file:// URL after [extractCoverToCache] runs once.
        coverUrl = coverUrl,
    )

private fun File.isImage(): Boolean = isFile && extension.lowercase() in IMAGE_EXTENSIONS

private fun File.isArchive(): Boolean = isFile && extension.lowercase() in ARCHIVE_EXTENSIONS

/** Stable id from the folder path, so rescans keep the same entry. */
fun localSeriesId(dir: File): String = "local:${dir.absolutePath.hashCode()}"

/**
 * Scans [root] for series folders. Each immediate child folder becomes one [LocalSeries]. A series
 * folder counts chapters as its chapter folders plus its archive files; loose images count as one
 * chapter. CBR/RAR is not supported: no RAR library ships with the app, so those folders are
 * skipped rather than half-read.
 */
fun scanLocalRoot(root: File): List<LocalSeries> {
    if (!root.isDirectory) return emptyList()
    return root.listFiles { file -> file.isDirectory && !file.name.startsWith(".") }
        .orEmpty()
        .sortedBy { it.name.lowercase() }
        .map { dir ->
            val chapters = localChapters(dir)
            LocalSeries(
                id = localSeriesId(dir),
                title = dir.name,
                dir = dir,
                cover = findLocalCover(dir, chapters),
                chapterCount = chapters.size,
            )
        }
}

/** The chapters of a series folder: chapter folders and archives, oldest first by name. */
fun localChapters(seriesDir: File): List<LocalChapter> {
    val seriesId = localSeriesId(seriesDir)
    val entries = seriesDir.listFiles { file ->
        !file.name.startsWith(".") && (file.isArchive() || (file.isDirectory && file.listFiles { it.isImage() }?.isNotEmpty() == true))
    }.orEmpty().sortedWith(compareBy({ it.isArchive() }, { it.name.lowercase() }))
    // Loose images directly in the series folder become a single "Oneshot" chapter.
    val loose = seriesDir.listFiles { file -> file.isImage() }.orEmpty().sortedBy { it.name.lowercase() }
    return entries.mapIndexed { index, file ->
        LocalChapter(
            id = "$seriesId:ch$index",
            seriesId = seriesId,
            title = file.nameWithoutExtension,
            order = index,
            source = file,
        )
    }.let { chapters ->
        if (loose.isEmpty()) chapters
        else chapters + LocalChapter(
            id = "$seriesId:loose",
            seriesId = seriesId,
            title = "Oneshot",
            order = chapters.size,
            source = seriesDir,
        )
    }
}

/** The pages of a chapter, in reading order. Archives stream through [ZipFile]; folders read directly. */
fun localChapterPages(chapter: LocalChapter): List<LocalPage> {
    val source = chapter.source
    return if (source.isArchive()) {
        ZipFile(source).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }
                .sortedBy { it.name.lowercase() }
                .map { LocalPage.ArchivePage(source, it.name) }
                .toList()
        }
    } else {
        source.listFiles { file -> file.isImage() }.orEmpty()
            .sortedBy { it.name.lowercase() }
            .map { LocalPage.FilePage(it) }
    }
}

/** A readable page of a local chapter: either a file on disk or an entry inside a CBZ. */
sealed interface LocalPage {
    data class FilePage(val file: File) : LocalPage
    data class ArchivePage(val archive: File, val entryName: String) : LocalPage

    /** Opens the page bytes. The caller closes the stream, which also releases the archive. */
    fun open(): java.io.InputStream = when (this) {
        is FilePage -> file.inputStream()
        is ArchivePage -> {
            val zip = ZipFile(archive)
            try {
                val entry = zip.getEntry(entryName) ?: throw java.io.IOException("Missing entry $entryName")
                val stream = zip.getInputStream(entry)
                object : java.io.FilterInputStream(stream) {
                    override fun close() {
                        try {
                            super.close()
                        } finally {
                            zip.close()
                        }
                    }
                }
            } catch (e: Exception) {
                zip.close()
                throw e
            }
        }
    }
}

private fun findLocalCover(seriesDir: File, chapters: List<LocalChapter>): LocalPage? {
    val named = seriesDir.listFiles { file -> file.isImage() && file.nameWithoutExtension.equals("cover", ignoreCase = true) }
        ?.minByOrNull { it.name }
    if (named != null) return LocalPage.FilePage(named)
    val first = chapters.firstOrNull() ?: return null
    val source = first.source
    return if (source.isArchive()) {
        ZipFile(source).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }
                .minByOrNull { it.name.lowercase() }
                ?.let { LocalPage.ArchivePage(source, it.name) }
        }
    } else {
        source.listFiles { file -> file.isImage() }
            ?.minByOrNull { it.name.lowercase() }
            ?.let { LocalPage.FilePage(it) }
    }
}

/**
 * Materializes a [LocalSeries.cover] into the app cache so the image pipeline gets a plain file.
 * Returns the file, or null when the cover is already a plain file (returned as-is) or unreadable.
 */
fun extractCoverToCache(series: LocalSeries, cacheDir: File): File? {
    val cover = series.cover ?: return null
    return when (cover) {
        is LocalPage.FilePage -> cover.file
        is LocalPage.ArchivePage -> extractToCache(cover.archive, cover.entryName, cacheDir, "cover_${series.id.hashCode()}")
    }
}

/**
 * Extracts one archive entry to the app cache so it can back a cover [File]. Callers pass the
 * app's cache dir and a stable file name per series.
 */
fun extractToCache(archive: File, entryName: String, cacheDir: File, name: String): File? {
    return try {
        val out = File(cacheDir, "localcovers").also { it.mkdirs() }.resolve(name)
        if (out.exists()) return out
        ZipFile(archive).use { zip ->
            val entry = zip.getEntry(entryName) ?: return null
            zip.getInputStream(entry).use { input -> out.writeAtomically { input.copyTo(it) } }
        }
        out
    } catch (e: Exception) {
        null
    }
}
