package com.dexter.notify

import com.dexter.data.LibraryData
import com.dexter.data.SavedSeries
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdatesWidgetTest {
    @Test
    fun unreadComeFirstThenNewest() {
        val library = LibraryData(
            subscribed = listOf(
                SavedSeries("a", "A", at = 30, knownChapterId = "x", knownChapterNumber = "5"),
                SavedSeries("b", "B", at = 20, knownChapterId = "y", knownChapterNumber = "9"),
                SavedSeries("c", "C", at = 10, knownChapterId = "z", knownChapterNumber = "3"),
                SavedSeries("d", "D", at = 40),
            ),
            recent = listOf(SavedSeries("a", "A", chapterNumber = "5"), SavedSeries("b", "B", chapterNumber = "8")),
        )
        val rows = widgetUpdates(library)
        assertEquals(listOf("b", "a", "c"), rows.map { it.seriesId })
        assertEquals(listOf(true, false, false), rows.map { it.unread })
    }
}
