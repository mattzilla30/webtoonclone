package com.dexter.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dexter.data.LibrarySeriesMeta
import com.dexter.data.ReadingStatus
import com.dexter.data.SavedSeries
import com.dexter.ui.series.hasUnreadChapters

/**
 * Surprise-me filters for the random pick: narrow the dice roll to what you are in the mood for.
 * [STATUS] values filter on the library's own reading-list status; [COMPLETED] on the series'
 * publication status from MangaDex metadata.
 */
enum class SurpriseFilter(val label: String) {
    ANY("Anything"),
    UNREAD("Has unread"),
    READING("Reading"),
    PLAN_TO_READ("Plan to read"),
    COMPLETED("Completed"),
}

/**
 * Picks one random series from [items] under [filter]. [lastReadNumber] maps series ids to the
 * last-read chapter number for the unread filter; [metas] carries publication statuses. Null when
 * nothing matches.
 */
fun pickRandomSeries(
    items: List<SavedSeries>,
    filter: SurpriseFilter,
    lastReadNumber: Map<String, String?> = emptyMap(),
    metas: Map<String, LibrarySeriesMeta> = emptyMap(),
): SavedSeries? {
    val pool = when (filter) {
        SurpriseFilter.ANY -> items
        SurpriseFilter.UNREAD -> items.filter {
            hasUnreadChapters(it.knownChapterNumber, lastReadNumber[it.id])
        }
        SurpriseFilter.READING -> items.filter { it.status == ReadingStatus.Reading }
        SurpriseFilter.PLAN_TO_READ -> items.filter { it.status == ReadingStatus.PlanToRead }
        SurpriseFilter.COMPLETED -> items.filter {
            metas[it.id]?.status?.equals("completed", ignoreCase = true) == true
        }
    }
    return pool.randomOrNull()
}

/**
 * The surprise-me dialog: filter chips plus a roll button. [onPick] receives the chosen series;
 * the dialog closes itself first. Drop-in for the dice button in the library top bar; see the
 * integration snippet in the task report.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SurpriseMeDialog(
    items: List<SavedSeries>,
    initial: SurpriseFilter = SurpriseFilter.ANY,
    lastReadNumber: Map<String, String?> = emptyMap(),
    metas: Map<String, LibrarySeriesMeta> = emptyMap(),
    onPick: (SavedSeries) -> Unit,
    onDismiss: () -> Unit,
) {
    var filter by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Surprise me") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Roll the dice on:")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SurpriseFilter.entries.forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text(option.label) },
                        )
                    }
                }
                val count = remember(items, filter, lastReadNumber, metas) {
                    when (filter) {
                        SurpriseFilter.ANY -> items.size
                        SurpriseFilter.UNREAD -> items.count { hasUnreadChapters(it.knownChapterNumber, lastReadNumber[it.id]) }
                        SurpriseFilter.READING -> items.count { it.status == ReadingStatus.Reading }
                        SurpriseFilter.PLAN_TO_READ -> items.count { it.status == ReadingStatus.PlanToRead }
                        SurpriseFilter.COMPLETED -> items.count { metas[it.id]?.status?.equals("completed", ignoreCase = true) == true }
                    }
                }
                Text(
                    if (count == 0) "Nothing matches this filter yet." else "$count series in the pool.",
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    pickRandomSeries(items, filter, lastReadNumber, metas)?.let {
                        onDismiss()
                        onPick(it)
                    }
                },
            ) { Text("Roll") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
