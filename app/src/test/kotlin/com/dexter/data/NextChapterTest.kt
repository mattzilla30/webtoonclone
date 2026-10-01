package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class NextChapterTest {
    private fun at(day: String) = "${day}T12:00:00+00:00"

    @Test
    fun aWeeklySeriesIsExpectedAWeekAfterItsLastChapter() {
        val dates = listOf(at("2026-09-28"), at("2026-09-21"), at("2026-09-14"), at("2026-09-07"))
        assertEquals(LocalDate.of(2026, 10, 5), nextChapterEstimate(dates, "ongoing", ZoneOffset.UTC))
    }

    @Test
    fun theUsualGapIgnoresOneLongBreak() {
        val dates = listOf(at("2026-09-28"), at("2026-09-21"), at("2026-08-01"), at("2026-07-25"), at("2026-07-18"))
        assertEquals(LocalDate.of(2026, 10, 5), nextChapterEstimate(dates, "ongoing", ZoneOffset.UTC))
    }

    @Test
    fun finishedOrSparseSeriesGetNoEstimate() {
        val dates = listOf(at("2026-09-28"), at("2026-09-21"), at("2026-09-14"))
        assertNull(nextChapterEstimate(dates, "completed", ZoneOffset.UTC))
        assertNull(nextChapterEstimate(dates.take(2), "ongoing", ZoneOffset.UTC))
    }

    @Test
    fun imageTypesAreReadFromTheirFirstBytes() {
        assertEquals("png", imageTypeOf(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0, 0, 0, 0)).second)
        assertEquals("jpg", imageTypeOf(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0)).second)
        assertEquals("Solo_Leveling_Ep_12", safeFileName("Solo Leveling, Ep. 12"))
    }
}
