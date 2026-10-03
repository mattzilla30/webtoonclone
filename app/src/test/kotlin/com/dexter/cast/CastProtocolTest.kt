package com.dexter.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CastProtocolTest {
    private val message = CastMessage("sender-0", "receiver-0", NS_CONNECTION, """{"type":"CONNECT"}""")

    @Test
    fun messagesRoundTrip() {
        assertEquals(message, decodeCastMessage(encodeCastMessage(message)))
    }

    @Test
    fun encodingMatchesTheProtobufLayout() {
        val bytes = encodeCastMessage(CastMessage("a", "b", "c", "d"))
        // version 0, source "a", destination "b", namespace "c", payload type 0, payload "d".
        val expected = byteArrayOf(0x08, 0x00, 0x12, 0x01, 'a'.code.toByte(), 0x1a, 0x01, 'b'.code.toByte(), 0x22, 0x01, 'c'.code.toByte(), 0x28, 0x00, 0x32, 0x01, 'd'.code.toByte())
        assertTrue(expected.contentEquals(bytes))
    }

    @Test
    fun framesStartWithTheBigEndianLength() {
        val frame = frameCastMessage(message)
        val length = ((frame[0].toInt() and 0xFF) shl 24) or ((frame[1].toInt() and 0xFF) shl 16) or ((frame[2].toInt() and 0xFF) shl 8) or (frame[3].toInt() and 0xFF)
        assertEquals(frame.size - 4, length)
    }

    @Test
    fun binaryPayloadsAreSkipped() {
        // A message with only a binary payload (field 7) carries nothing this app reads.
        assertNull(decodeCastMessage(byteArrayOf(0x3a, 0x01, 0x00)))
    }

    @Test
    fun imageTypesComeFromTheExtension() {
        assertEquals("image/png", imageTypeFor("https://x/data/abc/p1.png?token=1"))
        assertEquals("image/jpeg", imageTypeFor("https://x/data/abc/p1"))
    }
}
