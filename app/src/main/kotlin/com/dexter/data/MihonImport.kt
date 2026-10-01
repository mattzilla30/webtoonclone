package com.dexter.data

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/** One field of a protobuf message: its number and its value, a Long for numbers and bytes for text and messages. */
private class ProtoField(val number: Int, val varint: Long, val bytes: ByteArray?)

/** Reads the top-level fields of one protobuf message. Unknown field types end the read. */
private fun protoFields(data: ByteArray): List<ProtoField> {
    val fields = ArrayList<ProtoField>()
    var at = 0
    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (at < data.size) {
            val b = data[at++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
        throw IllegalArgumentException("Truncated varint")
    }
    while (at < data.size) {
        val key = varint()
        val number = (key ushr 3).toInt()
        when ((key and 7).toInt()) {
            0 -> fields += ProtoField(number, varint(), null)
            1 -> {
                var value = 0L
                for (i in 0 until 8) value = value or ((data[at + i].toLong() and 0xFF) shl (8 * i))
                at += 8
                fields += ProtoField(number, value, null)
            }
            2 -> {
                val length = varint().toInt()
                require(length >= 0 && at + length <= data.size) { "Bad length" }
                fields += ProtoField(number, 0, data.copyOfRange(at, at + length))
                at += length
            }
            5 -> {
                var value = 0L
                for (i in 0 until 4) value = value or ((data[at + i].toLong() and 0xFF) shl (8 * i))
                at += 4
                fields += ProtoField(number, value, null)
            }
            else -> throw IllegalArgumentException("Unsupported wire type")
        }
    }
    return fields
}

private fun List<ProtoField>.text(number: Int): String? = firstOrNull { it.number == number }?.bytes?.decodeToString()

private val MANGA_PATH = Regex("""/(?:manga|title)/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})""")
private val CHAPTER_PATH = Regex("""/chapter/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})""")

/** A MangaDex series found in a Mihon or Tachiyomi backup. */
data class ImportedSeries(
    val id: String,
    val title: String,
    val coverUrl: String?,
    /** In the backup's library, as opposed to only in its history. */
    val favorite: Boolean,
    val collections: List<String>,
    /** The read chapter with the highest number, when any is read. */
    val lastReadChapterId: String?,
    val lastReadNumber: String?,
)

/** What a backup held: the MangaDex series in it, and how many series came from other sources. */
data class MihonBackup(val series: List<ImportedSeries>, val otherSources: Int)

/** A chapter number as MangaDex writes it: "12" for 12.0, "12.5" otherwise. */
private fun chapterNumberText(number: Float): String = if (number == number.toInt().toFloat()) number.toInt().toString() else number.toString()

/**
 * Reads a Mihon or Tachiyomi backup (.tachibk or .proto.gz). Series from MangaDex keep their library place,
 * categories, and last read chapter. Series from other sources are counted and left out.
 */
fun parseMihonBackup(file: ByteArray): MihonBackup {
    // Backups are gzipped, but a plain one reads too.
    val raw = if (file.size > 2 && file[0] == 0x1f.toByte() && file[1] == 0x8b.toByte()) {
        GZIPInputStream(ByteArrayInputStream(file)).use { it.readBytes() }
    } else {
        file
    }
    val top = protoFields(raw)
    // A category's order number is what a series lists to say it belongs there.
    val categories = top.filter { it.number == 2 && it.bytes != null }.associate { field ->
        val fields = protoFields(field.bytes!!)
        (fields.firstOrNull { it.number == 2 }?.varint ?: 0L) to fields.text(1).orEmpty()
    }
    var others = 0
    val series = top.filter { it.number == 1 && it.bytes != null }.mapNotNull { field ->
        val manga = protoFields(field.bytes!!)
        val id = manga.text(2)?.let { MANGA_PATH.find(it)?.groupValues?.get(1) }
        if (id == null) {
            others++
            return@mapNotNull null
        }
        val read = manga.filter { it.number == 16 && it.bytes != null }.map { protoFields(it.bytes!!) }
            .filter { chapter -> chapter.any { it.number == 4 && it.varint != 0L } }
            .mapNotNull { chapter ->
                val chapterId = chapter.text(1)?.let { CHAPTER_PATH.find(it)?.groupValues?.get(1) } ?: return@mapNotNull null
                val number = chapter.firstOrNull { it.number == 9 }?.let { java.lang.Float.intBitsToFloat(it.varint.toInt()) } ?: -1f
                chapterId to number
            }
            .maxByOrNull { it.second }
        ImportedSeries(
            id = id,
            title = manga.text(3).orEmpty().ifBlank { "Untitled" },
            coverUrl = manga.text(9)?.takeIf { it.startsWith("http") },
            favorite = manga.any { it.number == 100 && it.varint != 0L },
            collections = manga.filter { it.number == 17 }.flatMap { field ->
                // Repeated numbers may come one per field or packed into one.
                field.bytes?.let { packed -> protoFields(packedAsFields(packed)).map { it.varint } } ?: listOf(field.varint)
            }.mapNotNull { categories[it] }.filter { it.isNotBlank() },
            lastReadChapterId = read?.first,
            lastReadNumber = read?.second?.takeIf { it >= 0 }?.let(::chapterNumberText),
        )
    }
    return MihonBackup(series, others)
}

/** Turns packed varints into a run of field-1 varints, so the field reader can read them. */
private fun packedAsFields(packed: ByteArray): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    var at = 0
    while (at < packed.size) {
        out.write(0x08)
        do {
            val b = packed[at++].toInt() and 0xFF
            out.write(b)
        } while (b and 0x80 != 0 && at < packed.size)
    }
    return out.toByteArray()
}
