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
import com.dexter.data.formatBytes
import com.dexter.ui.iconTap

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onBack: () -> Unit,
    onOpenChapter: (seriesId: String, chapterId: String) -> Unit,
) {
    val groups by viewModel.groups.collectAsState()
    val total = groups.orEmpty().sumOf { it.bytes }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), modifier = Modifier.iconTap(onBack))
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(stringResource(R.string.downloads), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(formatBytes(total), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (total > 0) {
                Text(stringResource(R.string.remove_all), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable { viewModel.deleteAll() })
            }
        }
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
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(group.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${group.chapters.size} chapters, ${formatBytes(group.bytes)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(stringResource(R.string.remove), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { viewModel.deleteSeries(group.seriesId) })
                        }
                    }
                    items(group.chapters, key = { it.chapterId }) { chapter ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenChapter(chapter.seriesId, chapter.chapterId) }.padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                buildString {
                                    append("Ep. ${chapter.number}")
                                    if (chapter.title.isNotBlank()) append(" · ${chapter.title}")
                                },
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text(formatBytes(chapter.bytes), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp))
                            Text(stringResource(R.string.remove), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { viewModel.delete(chapter.chapterId) })
                        }
                    }
                }
            }
        }
    }
}
