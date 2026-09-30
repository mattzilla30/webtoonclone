package com.dexter.notify

import org.junit.Assert.assertEquals
import org.junit.Test

class DigestTest {
    @Test
    fun titleCountsChapters() {
        assertEquals("1 new chapter", digestTitle(1))
        assertEquals("3 new chapters", digestTitle(3))
    }

    @Test
    fun linesJoinChaptersOfOneSeries() {
        val lines = digestLines(listOf("A" to "5", "B" to "2", "A" to "6"))
        assertEquals(listOf("A: Ch. 5, Ch. 6", "B: Ch. 2"), lines)
    }
}
