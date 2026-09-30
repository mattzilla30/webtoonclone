package com.dexter.data

import com.dexter.data.db.AppDatabase
import com.dexter.data.db.ReadEventEntity
import kotlinx.coroutines.flow.Flow
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
    val perDay: List<Pair<LocalDate, Int>>,
    val topSeries: List<Pair<String, Int>>,
)

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
    return ReadingStats(
        total = events.size,
        last7Days = within(7),
        last30Days = within(30),
        streakDays = streak,
        perDay = (6L downTo 0L).map { today.minusDays(it).let { day -> day to (days[day] ?: 0) } },
        topSeries = events.groupingBy { it.seriesTitle }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(5)
            .map { it.key to it.value },
    )
}

class StatsStore(private val db: AppDatabase) {
    val stats: Flow<ReadingStats> get() = db.stats().observe().map { computeStats(it, LocalDate.now()) }

    suspend fun recordRead(chapterId: String, seriesId: String, seriesTitle: String) {
        db.stats().insert(ReadEventEntity(chapterId, seriesId, seriesTitle, System.currentTimeMillis()))
    }
}
