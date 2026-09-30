package com.dexter.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexter.R
import com.dexter.data.ReadingStats
import com.dexter.ui.iconTap
import com.dexter.ui.theme.Green
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit) {
    val stats by viewModel.stats.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), modifier = Modifier.iconTap(onBack))
            Text(stringResource(R.string.reading_stats), fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(start = 16.dp))
        }
        val current = stats
        if (current != null) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Figure("Chapters read", current.total.toString())
                    Figure("Last 7 days", current.last7Days.toString())
                    Figure("Last 30 days", current.last30Days.toString())
                    Figure("Day streak", current.streakDays.toString())
                }
                Text(stringResource(R.string.this_week), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                DayBars(current)
                if (current.topSeries.isNotEmpty()) {
                    Text(stringResource(R.string.most_read), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                    current.topSeries.forEach { (title, count) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(title, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text("$count", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(
                    "Counts start from when this feature was added. Older reads are not included.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Green)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Seven bars, one per day, scaled to the busiest day. */
@Composable
private fun DayBars(stats: ReadingStats) {
    val max = (stats.perDay.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        stats.perDay.forEach { (day, count) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$count", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.fillMaxWidth().height((70 * count / max).coerceAtLeast(2).dp).background(if (count > 0) Green else MaterialTheme.colorScheme.surfaceVariant)) {}
                Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), fontSize = 10.sp)
            }
        }
    }
}
