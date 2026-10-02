package com.dexter.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * The editable fields of a ComicInfo.xml, the metadata file comic readers such as Mihon and Kavita
 * read from a CBZ. [comicInfoXml] writes this on export; the functions below read it back and
 * rewrite it inside an existing archive, so you can fix a wrong series name or chapter number
 * without re-downloading anything.
 */
data class ComicInfo(
    val series: String = "",
    val number: String = "",
    val title: String = "",
    val volume: String? = null,
    val translator: String? = null,
    val pageCount: Int = 0,
    val web: String? = null,
) {
    /** The XML document for this metadata, matching what [comicInfoXml] writes. */
    fun toXml(): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<ComicInfo>\n")
        append("  <Series>${xmlEscape(series)}</Series>\n")
        append("  <Number>${xmlEscape(number)}</Number>\n")
        if (title.isNotBlank()) append("  <Title>${xmlEscape(title)}</Title>\n")
        volume?.let { append("  <Volume>${xmlEscape(it)}</Volume>\n") }
        translator?.let { append("  <Translator>${xmlEscape(it)}</Translator>\n") }
        append("  <PageCount>$pageCount</PageCount>\n")
        web?.let { append("  <Web>${xmlEscape(it)}</Web>\n") }
        append("</ComicInfo>\n")
    }
}

private fun xmlEscape(text: String) =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private val ComicInfoTags = setOf("Series", "Number", "Title", "Volume", "Translator", "PageCount", "Web")

/**
 * Parses a ComicInfo.xml document. Unknown tags are ignored, so files written by other apps still
 * read. Returns null when the document is not a ComicInfo at all.
 */
fun parseComicInfo(xmlText: String): ComicInfo? {
    return try {
        val parser = Xml.newPullParser()
        parser.setInput(xmlText.reader())
        var series = ""
        var number = ""
        var title = ""
        var volume: String? = null
        var translator: String? = null
        var pageCount = 0
        var web: String? = null
        var event = parser.eventType
        var inComicInfo = false
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    if (name == "ComicInfo") {
                        inComicInfo = true
                    } else if (inComicInfo && name in ComicInfoTags) {
                        val text = parser.nextText()
                        when (name) {
                            "Series" -> series = text
                            "Number" -> number = text
                            "Title" -> title = text
                            "Volume" -> volume = text.ifBlank { null }
                            "Translator" -> translator = text.ifBlank { null }
                            "PageCount" -> pageCount = text.toIntOrNull() ?: 0
                            "Web" -> web = text.ifBlank { null }
                        }
                        event = parser.eventType
                        continue
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "ComicInfo") inComicInfo = false
            }
            event = parser.next()
        }
        if (!inComicInfo && series.isBlank() && number.isBlank()) null
        else ComicInfo(series, number, title, volume, translator, pageCount, web)
    } catch (e: Exception) {
        null
    }
}

/**
 * Reads the ComicInfo.xml out of [cbz], or null when the archive has none or it does not parse.
 * Runs on IO.
 */
suspend fun readComicInfo(cbz: File): ComicInfo? = withContext(Dispatchers.IO) {
    try {
        ZipFile(cbz).use { zip ->
            val entry = zip.entries().asSequence().firstOrNull { it.name.equals("ComicInfo.xml", ignoreCase = true) }
                ?: return@withContext null
            val text = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText()
            parseComicInfo(text)
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Rewrites the ComicInfo.xml inside [cbz] with [info], keeping every other entry byte-identical
 * where the zip format allows. The archive is rebuilt into a temporary file and swapped in, so a
 * crash never leaves a half-written CBZ. Returns false when the archive is missing or unreadable.
 * Runs on IO.
 */
suspend fun writeComicInfo(cbz: File, info: ComicInfo): Boolean = withContext(Dispatchers.IO) {
    if (!cbz.isFile) return@withContext false
    val tmp = File(cbz.parentFile, "${cbz.name}.tmp")
    try {
        ZipFile(cbz).use { zip ->
            ZipOutputStream(tmp.outputStream().buffered()).use { out ->
                for (entry in zip.entries().asSequence()) {
                    if (entry.name.equals("ComicInfo.xml", ignoreCase = true)) continue
                    val copy = ZipEntry(entry.name)
                    copy.time = entry.time
                    copy.method = entry.method
                    if (entry.method == ZipEntry.STORED) {
                        copy.size = entry.size
                        copy.crc = entry.crc
                    }
                    out.putNextEntry(copy)
                    zip.getInputStream(entry).use { it.copyTo(out) }
                    out.closeEntry()
                }
                out.putNextEntry(ZipEntry("ComicInfo.xml"))
                out.write(info.toXml().toByteArray(Charsets.UTF_8))
                out.closeEntry()
            }
        }
        if (!tmp.renameTo(cbz)) {
            cbz.delete()
            if (!tmp.renameTo(cbz)) return@withContext false
        }
        true
    } catch (e: Exception) {
        tmp.delete()
        false
    }
}
