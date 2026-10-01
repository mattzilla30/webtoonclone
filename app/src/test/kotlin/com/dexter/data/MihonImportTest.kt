package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/** Writes protobuf by hand, the way Mihon's backups are laid out. */
private class Proto {
    val out = ByteArrayOutputStream()

    private fun varint(value: Long) {
        var v = value
        while (v and 0x7F.inv().toLong() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }

    fun number(field: Int, value: Long) = apply {
        varint((field shl 3).toLong())
        varint(value)
    }

    fun float(field: Int, value: Float) = apply {
        varint(((field shl 3) or 5).toLong())
        val bits = java.lang.Float.floatToIntBits(value)
        for (i in 0 until 4) out.write((bits ushr (8 * i)) and 0xFF)
    }

    fun bytes(field: Int, value: ByteArray) = apply {
        varint(((field shl 3) or 2).toLong())
        varint(value.size.toLong())
        out.write(value)
    }

    fun text(field: Int, value: String) = bytes(field, value.toByteArray())

    fun message(field: Int, value: Proto) = bytes(field, value.out.toByteArray())
}

class MihonImportTest {
    private val seriesId = "11111111-2222-3333-4444-555555555555"
    private val ch1 = "aaaaaaaa-2222-3333-4444-555555555555"
    private val ch2 = "bbbbbbbb-2222-3333-4444-555555555555"

    private fun backup(): ByteArray {
        val manga = Proto()
            .number(1, 2499283573021220255)
            .text(2, "/manga/$seriesId")
            .text(3, "Solo Leveling")
            .text(9, "https://uploads.mangadex.org/covers/$seriesId/cover.jpg")
            .message(16, Proto().text(1, "/chapter/$ch1").number(4, 1).float(9, 1f))
            .message(16, Proto().text(1, "/chapter/$ch2").number(4, 1).float(9, 2.5f))
            .message(16, Proto().text(1, "/chapter/cccccccc-2222-3333-4444-555555555555").number(4, 0).float(9, 3f))
            .number(17, 5)
            .number(100, 1)
        val other = Proto().number(1, 1).text(2, "/series/elsewhere").text(3, "Elsewhere")
        val top = Proto()
            .message(1, manga)
            .message(1, other)
            .message(2, Proto().text(1, "Favourites").number(2, 5))
        val zipped = ByteArrayOutputStream()
        GZIPOutputStream(zipped).use { it.write(top.out.toByteArray()) }
        return zipped.toByteArray()
    }

    @Test
    fun readsMangaDexSeriesFromABackup() {
        val result = parseMihonBackup(backup())
        assertEquals(1, result.otherSources)
        val series = result.series.single()
        assertEquals(seriesId, series.id)
        assertEquals("Solo Leveling", series.title)
        assertTrue(series.favorite)
        assertEquals(listOf("Favourites"), series.collections)
        assertEquals(ch2, series.lastReadChapterId)
        assertEquals("2.5", series.lastReadNumber)
    }
}
