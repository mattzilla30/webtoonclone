package com.dexter.data

import com.dexter.data.db.ReadEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatsExtrasTest {
    @Test
    fun durationsReadNaturally() {
        assertEquals("under a minute", formatDuration(30_000))
        assertEquals("12 min", formatDuration(12 * 60_000L))
        assertEquals("2 h", formatDuration(120 * 60_000L))
        assertEquals("2 h 5 min", formatDuration(125 * 60_000L))
    }

    @Test
    fun calendarEndsOnToday() {
        // 2026-10-01 is a Thursday.
        val today = LocalDate.of(2026, 10, 1)
        val weeks = calendarWeeks(today, 3)
        assertEquals(3, weeks.size)
        assertEquals(LocalDate.of(2026, 9, 14), weeks.first().first())
        assertEquals(today, weeks.last()[3])
        assertNull(weeks.last()[4])
    }

    @Test
    fun timeAndGenresAreCounted() {
        val today = LocalDate.of(2026, 10, 1)
        val at = today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val events = listOf(
            ReadEventEntity("c1", "s1", "A", at, 60_000, "Action"),
            ReadEventEntity("c2", "s1", "A", at, 120_000, "Action"),
            ReadEventEntity("c3", "s2", "B", at - 30L * 86_400_000, 60_000, "Romance"),
        )
        val stats = computeStats(events, today, ZoneOffset.UTC)
        assertEquals(240_000L, stats.totalTimeMs)
        assertEquals(180_000L, stats.last7DaysTimeMs)
        assertEquals(listOf("Action" to 2, "Romance" to 1), stats.topGenres)
        assertEquals("A" to 180_000L, stats.timeBySeries.first())
    }
}
