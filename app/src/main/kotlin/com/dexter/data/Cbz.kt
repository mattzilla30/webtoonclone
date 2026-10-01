package com.dexter.data

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.dexter.data.db.DownloadEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Escapes text for an XML element. */
private fun xml(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** The ComicInfo.xml that comic readers such as Mihon and Kavita read for a chapter's series, number, and title. */
fun comicInfoXml(row: DownloadEntity): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<ComicInfo>\n")
    append("  <Series>${xml(row.seriesTitle)}</Series>\n")
    append("  <Number>${xml(row.number)}</Number>\n")
    if (row.title.isNotBlank()) append("  <Title>${xml(row.title)}</Title>\n")
    row.volume?.let { append("  <Volume>${xml(it)}</Volume>\n") }
    row.groupName?.let { append("  <Translator>${xml(it)}</Translator>\n") }
    append("  <PageCount>${row.pageCount}</PageCount>\n")
    append("  <Web>https://mangadex.org/chapter/${row.chapterId}</Web>\n")
    append("</ComicInfo>\n")
}

/** The file name for a chapter's CBZ, such as "Solo Leveling Ep 12.cbz", with characters file systems reject removed. */
fun cbzName(row: DownloadEntity): String =
    "${row.seriesTitle} Ep ${row.number}".replace(Regex("[\\\\/:*?\"<>|]+"), " ").replace(Regex("\\s+"), " ").trim().take(120) + ".cbz"

/** Packs saved chapters into CBZ files in Downloads/Dexter, one per chapter. */
class CbzExport(private val context: Context) {
    /** Writes [row]'s page files from [dir] into a CBZ. Returns false when the files are missing. */
    suspend fun export(row: DownloadEntity, dir: File): Boolean = withContext(Dispatchers.IO) {
        val pages = dir.listFiles()?.sortedBy { it.name }.orEmpty()
        if (pages.isEmpty()) return@withContext false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, cbzName(row))
            put(MediaStore.Downloads.MIME_TYPE, "application/vnd.comicbook+zip")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Dexter")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return@withContext false
        try {
            resolver.openOutputStream(uri)!!.use { out ->
                ZipOutputStream(out.buffered()).use { zip ->
                    // Images are already compressed, so storing them saves time and loses nothing.
                    zip.setLevel(0)
                    pages.forEach { page ->
                        zip.putNextEntry(ZipEntry(page.name))
                        page.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                    zip.setLevel(6)
                    zip.putNextEntry(ZipEntry("ComicInfo.xml"))
                    zip.write(comicInfoXml(row).toByteArray())
                    zip.closeEntry()
                }
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }
}
