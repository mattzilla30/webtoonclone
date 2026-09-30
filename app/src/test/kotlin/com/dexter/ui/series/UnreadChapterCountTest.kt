package com.dexter.ui.series

import com.dexter.data.Chapter
import org.junit.Assert.assertEquals
import org.junit.Test

class UnreadChapterCountTest {
    private fun chapter(number: String, external: String? = null) = Chapter("c$number", number, "", "2024-01-01T00:00:00+00:00", externalUrl = external)

    private val chapters = listOf(chapter("5"), chapter("4"), chapter("3", external = "https://pub"), chapter("2"), chapter("1"))

    @Test
    fun countsReadableChaptersAfterTheLastRead() {
        assertEquals(2, unreadChapterCount(chapters, "2"))
    }

    @Test
    fun nothingReadMeansNothingToCount() {
        assertEquals(0, unreadChapterCount(chapters, null))
    }

    @Test
    fun caughtUpIsZero() {
        assertEquals(0, unreadChapterCount(chapters, "5"))
    }
}
