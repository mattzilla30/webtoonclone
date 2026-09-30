package com.dexter.ui.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.dexter.data.formatBytes
import com.dexter.ui.AppTopBar
import com.dexter.ui.iconTap

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onBack: () -> Unit,
    onOpenChapter: (seriesId: String, chapterId: String) -> Unit,
    onOpenSeries: (seriesId: String) -> Unit,
) {
    val groups by viewModel.groups.collectAsState()
    val total = groups.orEmpty().sumOf { it.bytes }
    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            stringResource(R.string.downloads),
            onBack,
            subtitle = formatBytes(total),
            actions = {
                if (total > 0) {
                    androidx.compose.material3.TextButton(onClick = { viewModel.deleteAll() }) { Text(stringResource(R.string.remove_all)) }
                }
            },
        )
        val list = groups
        if (list.isNullOrEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (list == null) "" else "Saved chapters show up here. Long-press a chapter on a series page to save it.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
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
                            TextButton(onClick = { viewModel.deleteSeries(group.seriesId) }) { Text(stringResource(R.string.remove)) }
                        }
                    }
                    items(group.chapters, key = { it.chapterId }) { chapter ->
                        Surface(
                            onClick = { onOpenChapter(chapter.seriesId, chapter.chapterId) },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
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
                                TextButton(onClick = { viewModel.delete(chapter.chapterId) }) { Text(stringResource(R.string.remove)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
