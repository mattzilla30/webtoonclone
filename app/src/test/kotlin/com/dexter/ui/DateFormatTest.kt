package com.dexter.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

class DateFormatTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun theDateFollowsTheLanguage() {
        assertEquals("Sep 30, 2026", formatChapterDate("2026-09-30T12:00:00+00:00", Locale.US, utc))
        assertEquals("30.09.2026", formatChapterDate("2026-09-30T12:00:00+00:00", Locale.GERMANY, utc))
    }

    @Test
    fun theDateFollowsTheTimeZone() {
        assertEquals("Oct 1, 2026", formatChapterDate("2026-09-30T23:30:00+00:00", Locale.US, ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun anUnreadableDateIsEmpty() {
        assertEquals("", formatChapterDate("not a date", Locale.US, utc))
    }
}
