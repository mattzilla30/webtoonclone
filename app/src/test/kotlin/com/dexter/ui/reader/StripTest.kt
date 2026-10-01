package com.dexter.ui.reader

import com.dexter.data.Chapter
import org.junit.Assert.assertEquals
import org.junit.Test

class StripTest {
    private fun segment(id: String, pages: Int, index: Int) =
        ChapterSegment(Chapter(id, "$index", "", ""), List(pages) { "$id/$it" }, index, null, null)

    private val segments = listOf(segment("a", 2, 0), segment("b", 3, 1))
    private val strip = buildStrip(segments)

    @Test
    fun stripHasPagesHeadingsAndEnd() {
        assertEquals(listOf("p:a:0", "p:a:1", "d:b", "p:b:0", "p:b:1", "p:b:2", "end"), strip.map { it.key })
    }

    @Test
    fun cursorFollowsTheTopItem() {
        val counts = { s: Int -> segments[s].pages.size }
        assertEquals(Cursor(0, 1), cursorAt(strip, 1, counts))
        assertEquals(Cursor(1, 0), cursorAt(strip, 2, counts))
        assertEquals(Cursor(1, 2), cursorAt(strip, 6, counts))
    }

    @Test
    fun indexOfFindsPages() {
        assertEquals(4, stripIndexOf(strip, 1, 1))
        assertEquals(2, stripIndexOf(strip, 1, 9))
    }

    @Test
    fun spreadsMapBothWays() {
        assertEquals(0, pagerIndexOf(0, spreads = true))
        assertEquals(2, pagerIndexOf(4, spreads = true))
        assertEquals(3, pageOfPager(2, 6, spreads = true))
        assertEquals(5, pageOfPager(5, 6, spreads = false))
    }
}
