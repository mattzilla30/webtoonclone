package com.dexter.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexter.R
import com.dexter.data.ReadingStats
import com.dexter.ui.AppTopBar
import com.dexter.ui.iconTap
import java.time.format.TextStyle

@Composable
fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit) {
    val stats by viewModel.stats.collectAsState()
    val goal by viewModel.goal.collectAsState()
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
                Text(stringResource(R.string.this_week), style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                DayBars(current)
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
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

/** Seven bars, one per day, scaled to the busiest day. */
@Composable
private fun DayBars(stats: ReadingStats) {
    val max = (stats.perDay.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        stats.perDay.forEach { (day, count) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.fillMaxWidth().height((70 * count / max).coerceAtLeast(4).dp).clip(MaterialTheme.shapes.small).background(if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest))
                Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, LocalConfiguration.current.locales[0]), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
