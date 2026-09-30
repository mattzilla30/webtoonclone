package com.dexter.data

import com.dexter.data.db.ReadEventEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatsTest {
    private val today = LocalDate.of(2026, 9, 30)

    private fun event(id: String, daysAgo: Long, series: String = "A") =
        ReadEventEntity(id, series, series, today.minusDays(daysAgo).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli())

    @Test
    fun countsWindows() {
        val stats = computeStats(listOf(event("1", 0), event("2", 3), event("3", 10), event("4", 40)), today, ZoneOffset.UTC)
        assertEquals(4, stats.total)
        assertEquals(2, stats.last7Days)
        assertEquals(3, stats.last30Days)
    }

    @Test
    fun streakRunsThroughToday() {
        val stats = computeStats(listOf(event("1", 0), event("2", 1), event("3", 2), event("4", 5)), today, ZoneOffset.UTC)
        assertEquals(3, stats.streakDays)
    }

    @Test
    fun streakSurvivesUntilTheDayEnds() {
        val stats = computeStats(listOf(event("1", 1), event("2", 2)), today, ZoneOffset.UTC)
        assertEquals(2, stats.streakDays)
        assertEquals(0, computeStats(listOf(event("1", 3)), today, ZoneOffset.UTC).streakDays)
    }

    @Test
    fun perDayHasSevenEntriesOldestFirst() {
        val stats = computeStats(listOf(event("1", 0), event("2", 0)), today, ZoneOffset.UTC)
        assertEquals(7, stats.perDay.size)
        assertEquals(today.minusDays(6) to 0, stats.perDay.first())
        assertEquals(today to 2, stats.perDay.last())
    }

    @Test
    fun topSeriesOrdersByCountThenName() {
        val stats = computeStats(listOf(event("1", 0, "B"), event("2", 0, "A"), event("3", 0, "B")), today, ZoneOffset.UTC)
        assertEquals(listOf("B" to 2, "A" to 1), stats.topSeries)
    }

    @Test
    fun longestStreakFindsTheBestRun() {
        val events = listOf(event("1", 20), event("2", 19), event("3", 18), event("4", 17), event("5", 3), event("6", 2))
        assertEquals(4, computeStats(events, today, ZoneOffset.UTC).longestStreakDays)
        assertEquals(0, longestStreak(emptySet()))
    }

    @Test
    fun averageIsChaptersPerDayOverThirtyDays() {
        val events = (1..15).map { event("e$it", it.toLong()) }
        assertEquals(0.5, computeStats(events, today, ZoneOffset.UTC).averagePerDay, 0.0001)
    }
}
