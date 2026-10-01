package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorLogTest {
    @Test
    fun keepsTheNewestEntries() {
        val entries = (1..5).fold(emptyList<ErrorEntry>()) { list, n -> withEntry(list, ErrorEntry(n.toLong(), "k", "m$n", ""), max = 3) }
        assertEquals(listOf("m5", "m4", "m3"), entries.map { it.message })
    }

    @Test
    fun traceIsShortened() {
        val trace = shortTrace(IllegalStateException("boom"), lines = 3)
        assertEquals(3, trace.lines().size)
        assertTrue(trace.startsWith("java.lang.IllegalStateException: boom"))
    }

    @Test
    fun textListsEachEntry() {
        val text = errorLogText(listOf(ErrorEntry(0, "Crash", "Boom", "at x"))) { "time" }
        assertEquals("time  Crash\nBoom\nat x", text)
    }
}
