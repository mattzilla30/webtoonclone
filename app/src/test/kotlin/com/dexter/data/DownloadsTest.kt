package com.dexter.data

import com.dexter.data.db.DownloadEntity
import com.dexter.ui.downloads.groupDownloads
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadsTest {
    private fun chapter(number: String, id: String = "c$number", external: String? = null) =
        Chapter(id, number, "", "2024-01-01T00:00:00+00:00", externalUrl = external)

    private fun row(chapter: String, series: String, number: String, savedAt: Long = 0, bytes: Long = 10) = DownloadEntity(
        chapter, series, "Series $series", null, number, "", null, null, "2024-01-01T00:00:00+00:00", 3, bytes, savedAt,
    )

    @Test
    fun pageFilesSortInReadingOrder() {
        assertEquals("000.jpg", pageFileName(0, 12, "https://x/data/h/a.jpg"))
        assertEquals("011.png", pageFileName(11, 12, "https://x/data/h/b.png?token=1"))
        assertEquals("0001.webp", pageFileName(1, 1200, "https://x/c.webp"))
        assertEquals("000.img", pageFileName(0, 5, "https://x/noextension"))
    }

    @Test
    fun nextUnreadComesOldestFirst() {
        val newestFirst = listOf(chapter("5"), chapter("4"), chapter("3"), chapter("2"), chapter("1"))
        val picked = chaptersToDownload(newestFirst, lastReadNumber = "2", savedIds = emptySet(), count = 2)
        assertEquals(listOf("3", "4"), picked.map { it.number })
    }

    @Test
    fun savedAndLinkOutChaptersAreSkipped() {
        val newestFirst = listOf(chapter("3"), chapter("2", external = "https://pub"), chapter("1"))
        val picked = chaptersToDownload(newestFirst, lastReadNumber = null, savedIds = setOf("c1"), count = null)
        assertEquals(listOf("3"), picked.map { it.number })
    }

    @Test
    fun savedRowsBecomeAnOldestFirstList() {
        val rows = listOf(row("b", "s", "10"), row("a", "s", "2"))
        assertEquals(listOf("2", "10"), savedChapters(rows).map { it.number })
    }

    @Test
    fun downloadsGroupBySeriesWithNewestFirst() {
        val groups = groupDownloads(listOf(row("a", "x", "1", savedAt = 1), row("b", "y", "1", savedAt = 9), row("c", "x", "2", savedAt = 2, bytes = 5)))
        assertEquals(listOf("y", "x"), groups.map { it.seriesId })
        assertEquals(15L, groups[1].bytes)
    }
}
