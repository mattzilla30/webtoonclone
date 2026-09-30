package com.webtoonclone.ui.series

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadStateTest {
    @Test
    fun chaptersUpToTheLastReadOneAreRead() {
        assertTrue(isChapterRead("1", "10"))
        assertTrue(isChapterRead("10", "10"))
        assertFalse(isChapterRead("11", "10"))
    }

    @Test
    fun fractionalChaptersCompareAsNumbers() {
        assertTrue(isChapterRead("9.5", "10"))
        assertFalse(isChapterRead("10.5", "10"))
        assertTrue(isChapterRead("2", "10.5"))
    }

    @Test
    fun nothingIsReadWithoutProgressOrANumber() {
        assertFalse(isChapterRead("1", null))
        assertFalse(isChapterRead("Oneshot", "10"))
        assertFalse(isChapterRead("3", "Oneshot"))
    }

    @Test
    fun aNewerKnownChapterCountsAsUnread() {
        assertTrue(hasUnreadChapters("11", "10"))
        assertTrue(hasUnreadChapters("10.5", "10"))
        assertFalse(hasUnreadChapters("10", "10"))
        assertFalse(hasUnreadChapters("9", "10"))
    }

    @Test
    fun unreadNeedsBothNumbers() {
        assertFalse(hasUnreadChapters(null, "10"))
        assertFalse(hasUnreadChapters("11", null))
        assertFalse(hasUnreadChapters("Oneshot", "10"))
    }

    private fun chapter(id: String, external: Boolean = false) =
        com.webtoonclone.data.Chapter(id, id, "", "", if (external) "https://example.com" else null)

    @Test
    fun theNextOlderReadableChapterBecomesTheLastRead() {
        val list = listOf(chapter("c3"), chapter("c2", external = true), chapter("c1"))
        assertEquals("c1", previousReadable(list, list[0])?.id)
    }

    @Test
    fun theOldestChapterHasNoPreviousOne() {
        val list = listOf(chapter("c2"), chapter("c1"))
        assertEquals(null, previousReadable(list, list[1]))
    }
}
