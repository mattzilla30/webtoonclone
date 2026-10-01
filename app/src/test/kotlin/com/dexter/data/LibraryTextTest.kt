package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryTextTest {
    @Test
    fun groupsAndSortsTitles() {
        val library = LibraryData(
            subscribed = listOf(SavedSeries("1", "zebra"), SavedSeries("2", "Apple")),
            lists = listOf(SavedSeries("3", "Mango", status = ReadingStatus.Completed)),
            collections = mapOf("Faves" to listOf(SavedSeries("4", "Pear"))),
        )
        assertEquals(
            "Subscribed (2)\n- Apple\n- zebra\n\nCompleted (1)\n- Mango\n\nFaves (1)\n- Pear",
            libraryText(library),
        )
    }

    @Test
    fun aLibraryWithOnlyRecentReadsStillShares() {
        val library = LibraryData(recent = listOf(SavedSeries("1", "zebra"), SavedSeries("2", "Apple")))
        assertEquals("Recently read (2)\n- Apple\n- zebra", libraryText(library))
    }

    @Test
    fun emptyLibraryIsEmptyText() {
        assertEquals("", libraryText(LibraryData()))
    }
}
