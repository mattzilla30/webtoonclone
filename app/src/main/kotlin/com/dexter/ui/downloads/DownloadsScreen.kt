package com.dexter.ui.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexter.R
import com.dexter.data.ComicInfo
import com.dexter.data.db.DownloadEntity
import com.dexter.data.formatBytes
import com.dexter.ui.AppTopBar
import com.dexter.ui.ChoiceChip
import com.dexter.ui.ConfirmDialog
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onBack: () -> Unit,
    onOpenChapter: (seriesId: String, chapterId: String) -> Unit,
    onOpenSeries: (seriesId: String) -> Unit,
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val active by viewModel.active.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val integrity by viewModel.integrity.collectAsStateWithLifecycle()
    val total = groups.orEmpty().sumOf { it.bytes }
    var confirmRemoveAll by rememberSaveable { mutableStateOf(false) }
    // The downloaded chapter whose ComicInfo metadata is being edited.
    var editingMetadata by remember { mutableStateOf<DownloadEntity?>(null) }
    if (confirmRemoveAll) {
        ConfirmDialog(
            title = stringResource(R.string.remove_all_downloads_title),
            text = stringResource(R.string.remove_all_downloads_text, formatBytes(total)),
            confirmLabel = stringResource(R.string.remove_all),
            onConfirm = viewModel::deleteAll,
            onDismiss = { confirmRemoveAll = false },
        )
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                stringResource(R.string.downloads),
                onBack,
                subtitle = formatBytes(total),
                actions = {
                    if (total > 0) {
                        TextButton(onClick = { confirmRemoveAll = true }) { Text(stringResource(R.string.remove_all)) }
                    }
                },
            )
            val list = groups
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "options") {
                    DownloadOptions(
                        deleteAfterRead = settings.deleteAfterRead,
                        capMb = settings.downloadCapMb,
                        onDeleteAfterRead = viewModel::setDeleteAfterRead,
                        onCap = viewModel::setCap,
                    )
                }
                if (queue.isNotEmpty()) {
                    item(key = "queue-head") {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Waiting to save (${queue.size})", style = MaterialTheme.typography.titleSmallEmphasized, modifier = Modifier.weight(1f))
                            TextButton(onClick = viewModel::cancelAll) { Text("Cancel all") }
                        }
                    }
                    items(queue, key = { "q-${it.chapterId}" }) { item ->
                        val progress = active[item.chapterId]
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.animateItem().fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
                        ) {
                            Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.seriesTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                        Text(
                                            if (progress != null) "Ep. ${item.number} · ${(progress * 100).toInt()}%" else "Ep. ${item.number} · waiting",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (progress == null && item != queue.first()) {
                                        TextButton(onClick = { viewModel.moveToTop(item.chapterId) }) { Text("Next") }
                                    }
                                    TextButton(onClick = { viewModel.cancel(item.chapterId) }) { Text("Cancel") }
                                }
                                if (progress != null) {
                                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp))
                                }
                            }
                        }
                    }
                }
                if (list != null && list.isEmpty() && queue.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "Saved chapters show up here. Long-press a chapter on a series page to save it.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                }
                if (list != null) {
                    list.forEach { group ->
                        item(key = "series-${group.seriesId}") {
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpenSeries(group.seriesId) }.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(group.title, style = MaterialTheme.typography.titleSmallEmphasized)
                                    Text("${group.chapters.size} chapters, ${formatBytes(group.bytes)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { viewModel.verifySeries(group.seriesId) }) { Text("Verify") }
                                TextButton(onClick = { viewModel.exportCbz(group.chapters.map { it.chapterId }) }) { Text("CBZ") }
                                TextButton(onClick = { viewModel.deleteSeries(group.seriesId) }) { Text(stringResource(R.string.remove)) }
                            }
                        }
                        // Chapters that failed verification, with a repair button each.
                        val problems = integrity?.get(group.seriesId).orEmpty()
                        if (problems.isNotEmpty()) {
                            item(key = "integrity-${group.seriesId}") {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                                    problems.forEach { report ->
                                        val chapter = group.chapters.firstOrNull { it.chapterId == report.chapterId }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    "Ep. ${chapter?.number ?: "?"}: ${report.badPages.size} bad ${if (report.badPages.size == 1) "page" else "pages"}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                                Text(
                                                    report.badPages.take(3).joinToString { "${it.index + 1} (${it.problem.name.lowercase()})" },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            TextButton(onClick = { viewModel.repairChapter(group.seriesId, report) }) { Text("Repair") }
                                        }
                                    }
                                }
                            }
                        }
                        items(group.chapters, key = { it.chapterId }) { chapter ->
                            Surface(
                                onClick = { onOpenChapter(chapter.seriesId, chapter.chapterId) },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.animateItem().fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
                            ) {
                                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            buildString {
                                                append("Ep. ${chapter.number}")
                                                if (chapter.title.isNotBlank()) append(" · ${chapter.title}")
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(formatBytes(chapter.bytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = { viewModel.exportCbz(listOf(chapter.chapterId)) }) { Text("CBZ") }
                                    TextButton(onClick = { editingMetadata = chapter }) { Text("Edit") }
                                    TextButton(onClick = { viewModel.delete(chapter.chapterId) }) { Text(stringResource(R.string.remove)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        toast?.let { message ->
            LaunchedEffect(message) {
                delay(3.seconds)
                viewModel.clearToast()
            }
            Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Text(message) }
        }
        // The ComicInfo metadata editor: edits land on the saved chapter and in the next CBZ export.
        editingMetadata?.let { row ->
            ComicInfoEditorDialog(
                fileName = "Ep. ${row.number} · ${row.seriesTitle}",
                info = ComicInfo(
                    series = row.seriesTitle,
                    number = row.number,
                    title = row.title,
                    volume = row.volume,
                    translator = row.groupName,
                    pageCount = row.pageCount,
                ),
                onSave = { info ->
                    viewModel.updateMetadata(row.chapterId, info)
                    editingMetadata = null
                },
                onDismiss = { editingMetadata = null },
            )
        }
    }
}

private val capChoices = listOf(0L to "No limit", 500L to "500 MB", 1024L to "1 GB", 2048L to "2 GB", 5120L to "5 GB")

/** Whether read chapters are deleted, and the most space saved chapters may use. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DownloadOptions(deleteAfterRead: Boolean, capMb: Long, onDeleteAfterRead: (Boolean) -> Unit, onCap: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Delete a chapter once you open the next one", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = deleteAfterRead, onCheckedChange = onDeleteAfterRead)
        }
        Text("Space limit. The oldest saved chapters go first.", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            capChoices.forEach { (mb, label) -> ChoiceChip(label, capMb == mb) { onCap(mb) } }
        }
    }
}
