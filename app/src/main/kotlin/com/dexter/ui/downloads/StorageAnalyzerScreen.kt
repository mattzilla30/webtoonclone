package com.dexter.ui.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.dexter.data.CleanupSuggestion
import com.dexter.data.StorageReport
import com.dexter.data.formatBytes
import com.dexter.data.suggestions
import com.dexter.ui.AppTopBar

/**
 * Where your storage went: per-series breakdown, largest chapters, and cleanup suggestions. Call
 * [com.dexter.data.analyzeStorage] on IO first, then hand the report here.
 *
 * @param finishedSeriesIds series marked Completed in the library, for the "finished series" hint.
 * @param onDeleteChapter removes one downloaded chapter; [chapterId] is its download id.
 * @param onDeleteSeries removes every downloaded chapter of [seriesId].
 */
@Composable
fun StorageAnalyzerScreen(
    report: StorageReport,
    finishedSeriesIds: Set<String>,
    onDeleteChapter: (chapterId: String) -> Unit,
    onDeleteSeries: (seriesId: String) -> Unit,
    onBack: () -> Unit,
) {
    val suggestions = remember(report, finishedSeriesIds) { report.suggestions(finishedSeriesIds) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(Modifier.fillMaxSize()) {
        AppTopBar("Storage", onBack = onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(
                    "${formatBytes(report.totalBytes)} in downloads",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (suggestions.isNotEmpty()) {
                item { Text("Cleanup suggestions", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                items(suggestions, key = { it.title + it.detail }) { suggestion ->
                    SuggestionCard(suggestion)
                }
            }
            if (report.series.isNotEmpty()) {
                item {
                    Text(
                        "By series",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            items(report.series, key = { it.seriesId }) { series ->
                val isOpen = series.seriesId in expanded
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().clickable { expanded = if (isOpen) expanded - series.seriesId else expanded + series.seriesId }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(series.seriesTitle, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${series.chapters.size} ${if (series.chapters.size == 1) "chapter" else "chapters"} · ${formatBytes(series.bytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { confirmDelete = series.seriesId to series.seriesTitle }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete all downloads of ${series.seriesTitle}")
                            }
                            Icon(if (isOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                        }
                        if (isOpen) {
                            series.chapters.forEach { chapter ->
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Ch. ${chapter.number}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Text(formatBytes(chapter.bytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    IconButton(onClick = { confirmDelete = chapter.chapterId to "chapter ${chapter.number} of ${series.seriesTitle}" }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete chapter ${chapter.number}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    confirmDelete?.let { (id, label) ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete $label?") },
            text = { Text("The downloaded files are removed from this device. Your library entry stays.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    // A chapter id and a series id never collide here: chapter ids are MangaDex UUIDs.
                    if (report.series.any { it.seriesId == id }) onDeleteSeries(id) else onDeleteChapter(id)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

/** One cleanup suggestion, with the space it would free. */
@Composable
private fun SuggestionCard(suggestion: CleanupSuggestion) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(suggestion.title, style = MaterialTheme.typography.bodyLarge)
                Text(suggestion.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatBytes(suggestion.bytes), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
