package com.dexter.data

import com.dexter.data.db.AppDatabase
import com.dexter.data.db.ReadEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What the stats screen shows. [perDay] covers the last seven days, oldest first. */
data class ReadingStats(
    val total: Int,
    val last7Days: Int,
    val last30Days: Int,
    val streakDays: Int,
    /** The longest run of days in a row with at least one chapter read, ever. */
    val longestStreakDays: Int,
    /** Chapters per day over the last 30 days. */
    val averagePerDay: Double,
    val perDay: List<Pair<LocalDate, Int>>,
    val topSeries: List<Pair<String, Int>>,
    /** Time in the reader, all told and over the last seven days. */
    val totalTimeMs: Long = 0,
    val last7DaysTimeMs: Long = 0,
    /** Chapters per day for every day with any, for the calendar. */
    val days: Map<LocalDate, Int> = emptyMap(),
    /** Chapters per genre, most first. Reads from before genres were kept are left out. */
    val topGenres: List<Pair<String, Int>> = emptyList(),
    /** Time per series, most first. */
    val timeBySeries: List<Pair<String, Long>> = emptyList(),
)

/** A reading time as "2 h 5 min", "12 min", or "under a minute". */
fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "under a minute"
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

/**
 * The calendar's weeks, oldest first, each Monday to Sunday, ending with the week of [today]. Days after
 * today are null, so the last column stops at today.
 */
fun calendarWeeks(today: LocalDate, weeks: Int): List<List<LocalDate?>> {
    val lastMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    return (weeks - 1 downTo 0).map { back ->
        val monday = lastMonday.minusWeeks(back.toLong())
        (0L..6L).map { offset -> monday.plusDays(offset).takeIf { !it.isAfter(today) } }
    }
}

/**
 * Counts chapters by day. The streak is the run of days with at least one chapter that ends today, or
 * yesterday when nothing is read yet today, so it survives until the day is over.
 */
fun computeStats(events: List<ReadEventEntity>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): ReadingStats {
    val days = events.groupingBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }.eachCount()
    fun within(n: Long) = days.filterKeys { !it.isAfter(today) && it.isAfter(today.minusDays(n)) }.values.sum()
    var cursor = if (today in days) today else today.minusDays(1)
    var streak = 0
    while (cursor in days) {
        streak++
        cursor = cursor.minusDays(1)
    }
    val last30 = within(30)
    val weekStart = today.minusDays(6)
    return ReadingStats(
        total = events.size,
        last7Days = within(7),
        last30Days = last30,
        streakDays = streak,
        longestStreakDays = longestStreak(days.keys),
        averagePerDay = last30 / 30.0,
        perDay = (6L downTo 0L).map { today.minusDays(it).let { day -> day to (days[day] ?: 0) } },
        topSeries = events.groupingBy { it.seriesTitle }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(5)
            .map { it.key to it.value },
        totalTimeMs = events.sumOf { it.durationMs },
        last7DaysTimeMs = events.filter { !Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate().isBefore(weekStart) }.sumOf { it.durationMs },
        days = days,
        topGenres = events.mapNotNull { it.genre }.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(8)
            .map { it.key to it.value },
        timeBySeries = events.groupBy { it.seriesTitle }.mapValues { (_, rows) -> rows.sumOf { it.durationMs } }
            .filterValues { it > 0 }.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key to it.value },
    )
}

class StatsStore(private val db: AppDatabase) {
    val stats: Flow<ReadingStats> get() = db.stats().observe().map { computeStats(it, LocalDate.now()) }.flowOn(Dispatchers.Default)

    suspend fun recordRead(chapterId: String, seriesId: String, seriesTitle: String, genre: String? = null) {
        db.stats().recordOpen(chapterId, seriesId, seriesTitle, System.currentTimeMillis(), genre)
    }

    /** Chapters opened today. */
    suspend fun readToday(zone: ZoneId = ZoneId.systemDefault()): Int {
        val today = LocalDate.now(zone)
        return db.stats().observe().first().count { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() == today }
    }

    /** Adds [ms] of reader time to a chapter already recorded by [recordRead]. */
    suspend fun addReadingTime(chapterId: String, ms: Long) {
        if (ms > 0) db.stats().addDuration(chapterId, ms)
    }
}

/** The longest run of consecutive days in [days]. */
fun longestStreak(days: Set<LocalDate>): Int {
    var best = 0
    for (day in days) {
        // Only count from the first day of a run, so each run is measured once.
        if (day.minusDays(1) in days) continue
        var length = 1
        while (day.plusDays(length.toLong()) in days) length++
        best = maxOf(best, length)
    }
    return best
}
