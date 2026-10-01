package com.dexter.data

import com.dexter.data.db.DownloadEntity
import com.dexter.ui.series.savingLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressAndQueueTest {
    @Test
    fun aPositionWithItsPageCountReadsBackAndGivesItsShare() {
        val raw = formatProgress("c", 9, 0.5f, total = 20)
        val back = parseProgress(raw)
        assertEquals(20, back.total)
        assertEquals(0.475f, back.share!!, 0.001f)
    }

    @Test
    fun olderPositionsStillReadAndHaveNoShare() {
        assertNull(parseProgress("c:3").share)
        assertEquals(0.25f, parseProgress("c:3:250").fraction, 0.001f)
    }

    @Test
    fun theNewChapterEstimateNeedsWholeNumbers() {
        assertEquals(3, newChapterEstimate("15", "12"))
        assertNull(newChapterEstimate("12.5", "12"))
        assertNull(newChapterEstimate("12", "12"))
        assertNull(newChapterEstimate(null, "12"))
    }

    private fun row(id: String, bytes: Long, savedAt: Long) =
        DownloadEntity(id, "s", "S", null, "1", "", null, null, "", 1, bytes, savedAt)

    @Test
    fun theCapDeletesTheOldestUntilItFitsAndKeepsTheNewest() {
        val rows = listOf(row("a", 50, 1), row("b", 50, 2), row("c", 50, 3))
        assertEquals(listOf("a"), chaptersOverCap(rows, capBytes = 100, keep = "c"))
        assertEquals(listOf("b"), chaptersOverCap(rows, capBytes = 100, keep = "a"))
        assertEquals(emptyList<String>(), chaptersOverCap(rows, capBytes = 200, keep = null))
    }

    @Test
    fun savingLabelsSayWhereAChapterIs() {
        assertEquals("Saved", savingLabel(true, null))
        assertEquals("Queued", savingLabel(false, 0f))
        assertEquals("Saving 42%", savingLabel(false, 0.42f))
        assertNull(savingLabel(false, null))
    }
}
