package com.dexter.ui.series

import com.dexter.data.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadStateTest {
    private fun chapter(number: String, external: Boolean = false) =
        Chapter("id$number", number, "", "", externalUrl = if (external) "https://example.com" else null)

    // Newest first, as the series page lists them. Chapter 3 only links out.
    private val chapters = listOf(chapter("5"), chapter("4"), chapter("3", external = true), chapter("2"), chapter("1"))

    @Test
    fun theMapMatchesTheOneByOneLookupForEveryChapter() {
        val map = previousReadableMap(chapters)
        chapters.forEach { assertEquals(it.number, previousReadable(chapters, it), map[it.id]) }
    }

    @Test
    fun theOldestChapterHasNoPreviousAndLinkOutsAreSkipped() {
        val map = previousReadableMap(chapters)
        assertNull(map["id1"])
        assertEquals("id2", map["id4"]?.id)
    }
}
