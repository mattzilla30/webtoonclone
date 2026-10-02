package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSyncTest {
    private fun chapters(vararg numbers: String) =
        numbers.mapIndexed { index, number -> Chapter("c$index", number, "", "") }

    @Test
    fun nextChapterIndexWithoutSkipTakesTheVeryNext() {
        val chapters = chapters("1", "2", "3")
        assertEquals(1, nextChapterIndex(chapters, 0, "1", skipRead = false))
        assertEquals(2, nextChapterIndex(chapters, 1, "2", skipRead = false))
        assertEquals(3, nextChapterIndex(chapters, 2, "2", skipRead = false))
    }

    @Test
    fun nextChapterIndexWithSkipJumpsPastReadChapters() {
        val chapters = chapters("1", "2", "3", "4")
        // From "1" with "2" read, "2" is skipped and "3" is next.
        assertEquals(2, nextChapterIndex(chapters, 0, "2", skipRead = true))
        // From "2" with "2" read, "3" is next.
        assertEquals(2, nextChapterIndex(chapters, 1, "2", skipRead = true))
        // Nothing read yet: the very next chapter.
        assertEquals(1, nextChapterIndex(chapters, 0, null, skipRead = true))
        // Everything read: past the end.
        assertEquals(4, nextChapterIndex(chapters, 0, "9", skipRead = true))
    }

    @Test
    fun nextChapterIndexNeverSkipsANumberItCannotParse() {
        val chapters = chapters("1", "special", "3")
        assertEquals(1, nextChapterIndex(chapters, 0, "2", skipRead = true))
    }

    @Test
    fun seriesUpdateDueHonorsThePerSeriesInterval() {
        val settings = Settings(seriesUpdateIntervals = mapOf("a" to 1440), checkIntervalMinutes = 30)
        val minute = 60_000L
        assertTrue(seriesUpdateDue("a", null, settings, now = 100 * minute))
        assertFalse(seriesUpdateDue("a", 100 * minute, settings, now = 100 * minute + 12 * 60 * minute))
        assertTrue(seriesUpdateDue("a", 100 * minute, settings, now = 100 * minute + 24 * 60 * minute))
    }

    @Test
    fun seriesUpdateDueFallsBackToTheGlobalInterval() {
        val settings = Settings(seriesUpdateIntervals = mapOf("a" to 0), checkIntervalMinutes = 30)
        val minute = 60_000L
        assertFalse(seriesUpdateDue("a", 100 * minute, settings, now = 100 * minute + 10 * minute))
        assertTrue(seriesUpdateDue("a", 100 * minute, settings, now = 100 * minute + 30 * minute))
        assertTrue(seriesUpdateDue("b", 100 * minute, settings, now = 100 * minute + 30 * minute))
    }

    @Test
    fun offlineFiltersKeepOnlySavedContent() {
        val series = listOf(SavedSeries("a", "A"), SavedSeries("b", "B"))
        assertEquals(listOf(series[0]), offlineSeries(series, setOf("a")))
        val chapters = chapters("1", "2").map { it.copy(id = "c${it.number}") }
        assertEquals(listOf(chapters[1]), offlineChapters(chapters, setOf("c2")))
    }

    @Test
    fun backupRoundTripKeepsHistoryAndDownloads() {
        val backup = Backup(
            savedAt = 5L,
            history = listOf(ReadEvent("c1", "a", "A", 10L, 20L, "Action")),
            downloads = listOf(SavedDownload("c1", "a", "A", null, "1", "", null, null, "", 3, 9L, 11L)),
        )
        val decoded = decodeBackup(encodeBackup(backup))!!
        assertEquals(backup.history, decoded.history)
        assertEquals(backup.downloads, decoded.downloads)
        assertEquals(2, decoded.version)
    }

    @Test
    fun oldVersionOneBackupsStillDecode() {
        val decoded = decodeBackup("""{"version":1,"savedAt":7,"library":{},"settings":{},"progress":{}}""")
        assertEquals(1, decoded?.version)
        assertTrue(decoded?.history.isNullOrEmpty())
    }
}
