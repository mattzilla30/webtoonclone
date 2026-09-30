package com.dexter.ui.library

import com.dexter.data.LibraryData
import com.dexter.data.SavedSeries
import org.junit.Assert.assertEquals
import org.junit.Test

class UnreadCountTest {
    @Test
    fun countsSubscribedSeriesWithNewerChapters() {
        val library = LibraryData(
            recent = listOf(SavedSeries("a", "A", chapterNumber = "5"), SavedSeries("b", "B", chapterNumber = "9")),
            subscribed = listOf(
                SavedSeries("a", "A", knownChapterNumber = "7"),
                SavedSeries("b", "B", knownChapterNumber = "9"),
                SavedSeries("c", "C", knownChapterNumber = "3"),
            ),
        )
        // A is behind, B is caught up, and C was never opened so it has nothing to point at.
        assertEquals(1, unreadSeriesCount(library))
    }

    @Test
    fun emptyLibraryHasNone() {
        assertEquals(0, unreadSeriesCount(LibraryData()))
    }
}
