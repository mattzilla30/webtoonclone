package com.dexter.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class DayHeadingTest {
    private val today = LocalDate.of(2026, 10, 1)

    private fun heading(iso: String) = dayHeading(iso, today, ZoneOffset.UTC, Locale.US)

    @Test
    fun recentDaysGetNames() {
        assertEquals("Today", heading("2026-10-01T08:00:00+00:00"))
        assertEquals("Yesterday", heading("2026-09-30T23:00:00+00:00"))
        assertEquals("Monday", heading("2026-09-28T12:00:00+00:00"))
    }

    @Test
    fun olderDaysGetDatesAndBadTimesGoToEarlier() {
        assertEquals("Sep 1, 2026", heading("2026-09-01T12:00:00+00:00"))
        assertEquals("Earlier", heading("not a time"))
    }
}
