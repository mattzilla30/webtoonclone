package com.dexter.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val JSON_ENTRY = "backup.json"
private const val COVERS_DIR = "covers"
private const val DOWNLOADS_DIR = "downloads"

/** Restoring decompresses at most this much: more is a zip bomb, not a backup. */
private const val MAX_RESTORE_BYTES = 2L * 1024 * 1024 * 1024

/** Restoring reads at most this many entries: more is a zip bomb, not a backup. */
private const val MAX_RESTORE_ENTRIES = 200_000

/** The largest backup.json this reader accepts: the JSON is text, so more is not a backup. */
private const val MAX_JSON_BYTES = 64 * 1024 * 1024

/** The file name suggested when exporting a full backup archive. */
fun archiveFileName(): String = "dexter-backup.zip"

/** A series cover restored from a backup archive, or null when none was restored. */
fun backupCoverFile(context: Context, seriesId: String): File? =
    File(context.filesDir, COVERS_DIR).listFiles()?.firstOrNull { it.name.startsWith("$seriesId.") }

/**
 * A full backup as one zip file you can move to another device: the [Backup] JSON, every library
 * series' cover, and the page files of every downloaded chapter. Importing writes the data, then the
 * covers and pages, so the download rows restored from the JSON point at files that exist.
 *
 * There is no automatic cloud sync: you move the file yourself, by sharing it, a cable, or storage.
 */
class BackupArchive(
    private val context: Context,
    private val backupService: BackupService,
    private val downloads: DownloadStore,
    private val client: OkHttpClient,
) {
    /**
     * Writes the archive to [uri]. The zip is staged to a temp file first and only then copied
     * to [uri]: a failure while building it (a cover download hanging, the disk filling up) can
     * never truncate a previous backup at [uri] into a half-written zip.
     */
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val backup = backupService.create()
        val tmp = File(context.cacheDir, "backup-archive.tmp")
        try {
            tmp.writeAtomically { out ->
                ZipOutputStream(out.buffered()).use { zip ->
                    zip.putNextEntry(ZipEntry(JSON_ENTRY))
                    zip.write(encodeBackup(backup).toByteArray())
                    zip.closeEntry()
                    writeCovers(zip, coverUrls(backup))
                    for (row in backup.downloads) {
                        for (page in downloads.pageFiles(row.chapterId)) {
                            zip.putNextEntry(ZipEntry("$DOWNLOADS_DIR/${row.chapterId}/${page.name}"))
                            page.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }
            context.contentResolver.openOutputStream(uri, "wt")!!.use { out ->
                tmp.inputStream().use { it.copyTo(out) }
            }
        } finally {
            tmp.delete()
        }
    }

    /** Reads the backup out of an archive at [uri], or null when it is not one. Replaces nothing yet. */
    suspend fun readArchive(uri: Uri): Backup? = withContext(Dispatchers.IO) {
        var text: String? = null
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == JSON_ENTRY) text = zip.readCapped(MAX_JSON_BYTES).toString(Charsets.UTF_8)
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        text?.let(::decodeBackup)
    }

    /**
     * Restores [backup] from the archive at [uri]: covers and downloaded pages are written into place
     * first, then the data, so the restored download rows point at files that exist. Entries that
     * would escape the app's folders are skipped, and the restore aborts when the archive looks
     * like a zip bomb.
     */
    suspend fun restoreArchive(uri: Uri, backup: Backup) = withContext(Dispatchers.IO) {
        val coversRoot = File(context.filesDir, COVERS_DIR).also { it.mkdirs() }
        val clearedChapterDirs = HashSet<File>()
        var totalBytes = 0L
        var entryCount = 0
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (++entryCount > MAX_RESTORE_ENTRIES) throw IOException("Backup archive has too many entries")
                    val name = entry.name
                    if (!entry.isDirectory) when {
                        name.startsWith("$COVERS_DIR/") -> {
                            val target = safeFile(coversRoot, name.removePrefix("$COVERS_DIR/"))
                            if (target != null) {
                                target.parentFile?.mkdirs()
                                totalBytes = copyCapped(zip, target, totalBytes)
                            }
                        }
                        name.startsWith("$DOWNLOADS_DIR/") -> {
                            val rest = name.removePrefix("$DOWNLOADS_DIR/")
                            val target = chapterFile(rest.substringBefore('/'), rest.substringAfter('/', ""))
                            if (target != null) {
                                val dir = target.parentFile!!
                                // First page for this chapter: start from an empty dir, so stale
                                // pages from an earlier download can't linger and break prune()'s
                                // page count. Recorded hashes are dropped too: they belong to the
                                // old pages, and verification degrades to presence checks.
                                if (clearedChapterDirs.add(dir)) {
                                    dir.deleteRecursively()
                                    downloads.clearPageHashes(rest.substringBefore('/'))
                                }
                                dir.mkdirs()
                                totalBytes = copyCapped(zip, target, totalBytes)
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        // The download rows now point at files that exist; rows without files drop out.
        backupService.restore(backup)
    }

    /** The covers of every series in the backup's library, by series id. */
    private fun coverUrls(backup: Backup): Map<String, String> {
        val series = backup.library.recent + backup.library.subscribed + backup.library.lists +
            backup.library.collections.values.flatten()
        return series.mapNotNull { it.coverUrl?.let { url -> it.id to url } }.toMap()
    }

    /** Fetches each cover and writes it into the zip. One failing cover costs only that cover. */
    private fun writeCovers(zip: ZipOutputStream, urls: Map<String, String>) {
        for ((seriesId, url) in urls) {
            val bytes = runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) error("Cover failed: ${response.code}")
                    response.body.bytes()
                }
            }.getOrNull() ?: continue
            val extension = url.substringBefore('?').substringAfterLast('.', "jpg").takeIf { it.length in 2..4 } ?: "jpg"
            zip.putNextEntry(ZipEntry("$COVERS_DIR/$seriesId.$extension"))
            zip.write(bytes)
            zip.closeEntry()
        }
    }

    /** [name] as a file under [root], or null when it would escape [root]. */
    private fun safeFile(root: File, name: String): File? {
        if (name.isBlank() || ".." in name || name.startsWith('/')) return null
        val file = File(root, name).canonicalFile
        return file.takeIf { it.path.startsWith(root.canonicalPath + File.separator) }
    }

    /**
     * [fileName] as a page file inside [chapterId]'s folder, or null when either part could escape
     * the downloads folder. Both come from zip entry names, so each is validated as a single path
     * segment and the resolved file gets a canonical containment check against the downloads root.
     */
    private fun chapterFile(chapterId: String, fileName: String): File? {
        if (!isSafeSegment(chapterId) || !isSafeSegment(fileName)) return null
        val target = File(downloads.dirFor(chapterId), fileName).canonicalFile
        val root = downloads.rootDir.canonicalFile
        return target.takeIf { it.path.startsWith(root.path + File.separator) }
    }

    /** A single path segment: non-blank, no separators, no parent or self references. */
    private fun isSafeSegment(segment: String): Boolean =
        segment.isNotBlank() && segment != "." && segment != ".." &&
            '/' !in segment && '\\' !in segment

    /**
     * Copies the current zip entry to [target], aborting when the archive would decompress past
     * [MAX_RESTORE_BYTES]. Returns the running total. Entry sizes can't be trusted (a zip bomb
     * compresses gigabytes of zeros into kilobytes), so bytes are counted as they land.
     */
    private fun copyCapped(input: InputStream, target: File, totalSoFar: Long): Long {
        var total = totalSoFar
        target.outputStream().use { out ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_RESTORE_BYTES) throw IOException("Backup archive is too large to restore")
                out.write(buf, 0, n)
            }
        }
        return total
    }
}
