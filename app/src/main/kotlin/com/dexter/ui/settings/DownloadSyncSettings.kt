package com.dexter.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dexter.data.SavedSeries
import com.dexter.data.Settings
import com.dexter.ui.ChoiceChip

/** The check cadences a series can have, in minutes. 0 follows the global schedule. */
private val INTERVAL_OPTIONS = listOf(
    0 to "Global",
    60 to "Hourly",
    360 to "Every 6 hours",
    720 to "Every 12 hours",
    1440 to "Daily",
    4320 to "Every 3 days",
    10080 to "Weekly",
)

private fun intervalLabel(minutes: Int, global: Int): String = when (minutes) {
    0 -> "Global ($global min)"
    else -> INTERVAL_OPTIONS.firstOrNull { it.first == minutes }?.second ?: "Every $minutes min"
}

/**
 * Auto-downloads, skip-read advance, offline-only mode, per-series check intervals, and full backup
 * export/import. The screen that shows this passes its archive export/import launchers in.
 */
@Composable
fun DownloadSyncSection(
    settings: Settings,
    subscribed: List<SavedSeries>,
    update: ((Settings) -> Settings) -> Unit,
    onExportArchive: () -> Unit,
    onImportArchive: () -> Unit,
) {
    var intervalsOpen by remember { mutableStateOf(false) }

    SettingsBlock("Downloads") {
        SwitchRow(
            "Auto-download new chapters",
            "When a check finds new chapters of followed series, they save in the background. Follows the Wi-Fi-only setting and the storage cap.",
            settings.autoDownloadNew,
            keywords = listOf("followed", "offline", "save"),
        ) { on -> update { it.copy(autoDownloadNew = on) } }
        SwitchRow(
            "Skip chapters you already read",
            "The reader's next chapter jumps past chapters at or below the last one you read.",
            settings.skipReadChapters,
            keywords = listOf("next chapter", "read chapters"),
        ) { on -> update { it.copy(skipReadChapters = on) } }
        SwitchRow(
            "Offline only",
            "Library and chapter lists show only series and chapters saved on the device.",
            settings.offlineOnly,
            keywords = listOf("downloaded only", "airplane"),
        ) { on -> update { it.copy(offlineOnly = on) } }
        val custom = settings.seriesUpdateIntervals.count { it.value > 0 }
        InfoRow(
            "New chapter check per series",
            if (custom == 0) {
                "Every followed series follows the global schedule."
            } else {
                "$custom ${if (custom == 1) "series has" else "series have"} their own schedule."
            },
            onClick = { intervalsOpen = true },
            keywords = listOf("update interval", "check interval", "schedule"),
        )
        Setting(
            "Full backup archive",
            "Save everything, including history, stats, downloads, and covers, to one file you can move to another device by sharing it, a cable, or storage.",
            keywords = listOf("export", "import", "sync", "transfer", "zip", "cross-device"),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onExportArchive) { Text("Export backup") }
                OutlinedButton(onClick = onImportArchive) { Text("Import backup") }
            }
        }

        if (intervalsOpen) {
            SeriesIntervalDialog(
                subscribed = subscribed,
                intervals = settings.seriesUpdateIntervals,
                globalMinutes = settings.checkIntervalMinutes,
                onSet = { seriesId, minutes ->
                    update { current ->
                        current.copy(
                            seriesUpdateIntervals = if (minutes == 0) {
                                current.seriesUpdateIntervals - seriesId
                            } else {
                                current.seriesUpdateIntervals + (seriesId to minutes)
                            },
                        )
                    }
                },
                onDismiss = { intervalsOpen = false },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesIntervalDialog(
    subscribed: List<SavedSeries>,
    intervals: Map<String, Int>,
    globalMinutes: Int,
    onSet: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var expanded by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Check for new chapters") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "How often each followed series is checked. A custom interval longer than the global schedule applies; a shorter one checks as often as the schedule runs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                subscribed.forEach { series ->
                    val current = intervals[series.id] ?: 0
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { expanded = if (expanded == series.id) null else series.id }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(series.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    intervalLabel(current, globalMinutes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (expanded == series.id) {
                            FlowRow(
                                Modifier.padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                INTERVAL_OPTIONS.forEach { (minutes, label) ->
                                    ChoiceChip(label, minutes == current) {
                                        onSet(series.id, minutes)
                                        expanded = null
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
