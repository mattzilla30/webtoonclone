package com.dexter.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val JSON_ENTRY = "backup.json"
private const val COVERS_DIR = "covers"
private const val DOWNLOADS_DIR = "downloads"

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
    /** Writes the archive to [uri]. */
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val backup = backupService.create()
        context.contentResolver.openOutputStream(uri, "wt")!!.use { out ->
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
    }

    /** Reads the backup out of an archive at [uri], or null when it is not one. Replaces nothing yet. */
    suspend fun readArchive(uri: Uri): Backup? = withContext(Dispatchers.IO) {
        var text: String? = null
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == JSON_ENTRY) text = zip.readBytes().toString(Charsets.UTF_8)
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
     * would escape the app's folders are skipped.
     */
    suspend fun restoreArchive(uri: Uri, backup: Backup) = withContext(Dispatchers.IO) {
        val coversRoot = File(context.filesDir, COVERS_DIR).also { it.mkdirs() }
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val target = when {
                        name.startsWith("$COVERS_DIR/") -> safeFile(coversRoot, name.removePrefix("$COVERS_DIR/"))
                        name.startsWith("$DOWNLOADS_DIR/") -> {
                            val rest = name.removePrefix("$DOWNLOADS_DIR/")
                            val chapterId = rest.substringBefore('/')
                            val fileName = rest.substringAfter('/', "")
                            if (chapterId.isNotBlank() && fileName.isNotBlank()) safeFile(downloads.dirFor(chapterId), fileName) else null
                        }
                        else -> null
                    }
                    if (!entry.isDirectory && target != null) {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zip.copyTo(it) }
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
}
