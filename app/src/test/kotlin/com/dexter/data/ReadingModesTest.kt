package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingModesTest {
    @Test
    fun aLongStripTagMeansVerticalWhateverTheLanguage() {
        assertEquals(ReadingMode.Vertical, detectReadingMode(listOf("Long Strip", "Romance"), "ja"))
        assertEquals(ReadingMode.Vertical, detectReadingMode(listOf("Long Strip"), "ko"))
    }

    @Test
    fun japaneseWorksPageRightToLeft() {
        assertEquals(ReadingMode.PagedRtl, detectReadingMode(listOf("Action"), "ja"))
    }

    @Test
    fun otherPagedWorksReadLeftToRight() {
        assertEquals(ReadingMode.PagedLtr, detectReadingMode(listOf("Drama"), "ko"))
        assertEquals(ReadingMode.PagedLtr, detectReadingMode(emptyList(), "en"))
    }

    @Test
    fun anUnknownLanguageKeepsTheVerticalScroll() {
        assertEquals(ReadingMode.Vertical, detectReadingMode(listOf("Drama"), ""))
    }

    @Test
    fun aChoiceOverridesDetectionButAutoDoesNot() {
        assertEquals(ReadingMode.PagedLtr, resolveMode(ReadingMode.PagedLtr, ReadingMode.Vertical))
        assertEquals(ReadingMode.Vertical, resolveMode(ReadingMode.Auto, ReadingMode.Vertical))
        assertEquals(ReadingMode.PagedRtl, resolveMode(null, ReadingMode.PagedRtl))
    }

    @Test
    fun theMiddleThirdTogglesTheBars() {
        assertEquals(TapAction.ToggleBars, tapAction(500f, 1000f, rtl = false))
        assertEquals(TapAction.ToggleBars, tapAction(500f, 1000f, rtl = true))
    }

    @Test
    fun theSidesTurnThePageInTheReadingDirection() {
        assertEquals(TapAction.Previous, tapAction(100f, 1000f, rtl = false))
        assertEquals(TapAction.Next, tapAction(900f, 1000f, rtl = false))
        assertEquals(TapAction.Next, tapAction(100f, 1000f, rtl = true))
        assertEquals(TapAction.Previous, tapAction(900f, 1000f, rtl = true))
    }

    @Test
    fun anUnmeasuredScreenOnlyTogglesTheBars() {
        assertEquals(TapAction.ToggleBars, tapAction(10f, 0f, rtl = false))
    }

    private fun chapter(id: String, alternates: List<Chapter> = emptyList()) = Chapter(id, id, "", "", alternates = alternates)

    @Test
    fun aChapterIsFoundByItsOwnIdOrAnAlternateUpload() {
        val list = listOf(chapter("c1"), chapter("c2", alternates = listOf(chapter("c2-other"))))
        assertEquals(0, findChapter(list, "c1")?.first)
        assertEquals(1 to "c2", findChapter(list, "c2")?.let { it.first to it.second.id })
        assertEquals(1 to "c2-other", findChapter(list, "c2-other")?.let { it.first to it.second.id })
        assertNull(findChapter(list, "missing"))
    }
}
