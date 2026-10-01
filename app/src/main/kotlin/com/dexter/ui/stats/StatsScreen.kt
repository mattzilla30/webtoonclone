package com.dexter.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexter.R
import com.dexter.data.ReadingStats
import com.dexter.data.calendarWeeks
import com.dexter.data.formatDuration
import com.dexter.ui.AppTopBar
import java.time.LocalDate
import java.time.format.TextStyle

@Composable
fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val goal by viewModel.goal.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        AppTopBar(stringResource(R.string.reading_stats), onBack)
        val current = stats
        if (current != null) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                if (goal > 0) {
                    val today = current.perDay.lastOrNull()?.second ?: 0
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                if (today >= goal) "Goal reached: $today of $goal today" else "Today: $today of $goal chapters",
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            LinearProgressIndicator(
                                progress = { (today.toFloat() / goal).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Figure("Chapters read", current.total.toString())
                    Figure("Last 7 days", current.last7Days.toString())
                    Figure("Last 30 days", current.last30Days.toString())
                    Figure("Day streak", current.streakDays.toString())
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Figure("Best streak", current.longestStreakDays.toString())
                    Figure("Per day (30 days)", "%.1f".format(current.averagePerDay))
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Figure("Time reading", formatDuration(current.totalTimeMs))
                    Figure("Time, last 7 days", formatDuration(current.last7DaysTimeMs))
                }
                Text(stringResource(R.string.this_week), style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                DayBars(current)
                Text("Reading days", style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                ReadingCalendar(current.days)
                if (current.topGenres.isNotEmpty()) {
                    Text("Genres", style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                    val most = current.topGenres.first().second
                    current.topGenres.forEach { (genre, count) -> ShareRow(genre, "$count", count.toFloat() / most) }
                }
                if (current.timeBySeries.isNotEmpty()) {
                    Text("Most time", style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                    val most = current.timeBySeries.first().second
                    current.timeBySeries.forEach { (title, ms) -> ShareRow(title, formatDuration(ms), ms.toFloat() / most) }
                }
                if (current.topSeries.isNotEmpty()) {
                    Text(stringResource(R.string.most_read), style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                    current.topSeries.forEach { (title, count) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("$count", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(
                    "Counts start from when this feature was added. Older reads are not included.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun RowScope.Figure(label: String, value: String) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineMediumEmphasized)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

/** Seven bars, one per day, scaled to the busiest day. */
@Composable
private fun DayBars(stats: ReadingStats) {
    val max = (stats.perDay.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().heightIn(min = 110.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        stats.perDay.forEach { (day, count) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.fillMaxWidth().height((70 * count / max).coerceAtLeast(4).dp).clip(MaterialTheme.shapes.small).background(if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest))
                Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, LocalConfiguration.current.locales[0]), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** A label, a value, and a bar for its share of the largest one. */
@Composable
private fun ShareRow(label: String, value: String, share: Float) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(progress = { share.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
    }
}

private const val CALENDAR_WEEKS = 20

/** The last twenty weeks as a grid, one column per week and one row per weekday, darker for more chapters. */
@Composable
private fun ReadingCalendar(days: Map<LocalDate, Int>) {
    val weeks = remember { calendarWeeks(LocalDate.now(), CALENDAR_WEEKS) }
    val most = (days.values.maxOrNull() ?: 0).coerceAtLeast(1)
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val full = MaterialTheme.colorScheme.primary
    val read = weeks.flatten().count { it != null && (days[it] ?: 0) > 0 }
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$read reading days in the last $CALENDAR_WEEKS weeks" },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        weeks.forEach { week ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                week.forEach { day ->
                    val count = day?.let { days[it] } ?: 0
                    val color = when {
                        day == null -> Color.Transparent
                        count == 0 -> empty
                        else -> lerp(empty, full, 0.35f + 0.65f * count / most)
                    }
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.extraSmall).background(color))
                }
            }
        }
    }
}
