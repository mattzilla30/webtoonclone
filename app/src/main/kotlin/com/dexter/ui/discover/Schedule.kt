package com.dexter.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dexter.ui.Cover
import java.time.DayOfWeek
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** A followed series and the weekday its chapters usually land on. */
data class SeriesSchedule(
    val seriesId: String,
    val title: String,
    val coverUrl: String?,
    val weekday: DayOfWeek,
    /** How many recent chapters went into the call. */
    val chapters: Int,
)

/** How many chapters a weekday call needs before it counts as a pattern. */
private const val MIN_SCHEDULE_CHAPTERS = 3

/**
 * The weekday most of [publishedAt] fall on, or null when there are too few chapters or no clear
 * pattern. Takes ISO-8601 instants, as the chapter feed carries them, and reads them in [zone].
 * A "usual" weekday needs at least half the chapters behind it.
 */
fun typicalWeekday(publishedAt: List<String>, zone: ZoneId = ZoneId.systemDefault()): DayOfWeek? {
    if (publishedAt.size < MIN_SCHEDULE_CHAPTERS) return null
    val days = publishedAt.mapNotNull { raw ->
        runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(zone).dayOfWeek }.getOrNull()
    }
    if (days.isEmpty()) return null
    val top = days.groupingBy { it }.eachCount().maxBy { it.value }
    return top.key.takeIf { top.value * 2 >= days.size }
}

/** Groups schedules Monday-first for the weekly view. */
fun schedulesByWeekday(schedules: List<SeriesSchedule>): Map<DayOfWeek, List<SeriesSchedule>> =
    schedules.groupBy { it.weekday }.toSortedMap()

/**
 * The weekly schedule: followed series under the weekday their chapters usually land on.
 * Replaces the updates list when the Schedule view is on; see the integration snippet in the
 * feature report for the Updates screen wiring.
 */
@Composable
fun ScheduleList(schedules: List<SeriesSchedule>, onOpenSeries: (String) -> Unit, modifier: Modifier = Modifier) {
    val byDay = remember(schedules) { schedulesByWeekday(schedules) }
    val locale = Locale.getDefault()
    if (byDay.isEmpty()) {
        Text(
            "No clear weekly pattern yet. Series need a few chapters before their usual day shows.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.fillMaxSize().padding(32.dp),
        )
        return
    }
    LazyColumn(modifier.fillMaxSize()) {
        byDay.forEach { (day, series) ->
            item(key = "day-${day.name}") {
                Text(
                    day.getDisplayName(TextStyle.FULL, locale),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).semantics { heading() },
                )
            }
            items(series, key = { "schedule-${it.seriesId}" }) { scheduled ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenSeries(scheduled.seriesId) }.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(
                        scheduled.coverUrl,
                        null,
                        Modifier.width(40.dp).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.extraSmall),
                        contentScale = ContentScale.Crop,
                        thumb = true,
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(scheduled.title, style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Usually ${day.getDisplayName(TextStyle.FULL, locale).lowercase(locale)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
