package com.webtoonclone.ui.series

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.webtoonclone.ui.LoadView

@Composable
fun SeriesScreen(viewModel: SeriesViewModel, onOpenChapter: (chapterId: String) -> Unit) {
    val state by viewModel.state.collectAsState()
    val progress by viewModel.progress.collectAsState()

    LoadView(state, onRetry = viewModel::load) { page ->
        val resume = progress?.let { p -> page.chapters.find { it.id == p.chapterId } }
        LazyColumn(Modifier.padding(horizontal = 12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(
                        model = page.detail.summary.coverUrl,
                        contentDescription = page.detail.summary.title,
                        modifier = Modifier.width(120.dp),
                    )
                    Column {
                        Text(page.detail.summary.title, style = MaterialTheme.typography.titleLarge)
                        Text(page.detail.status, style = MaterialTheme.typography.labelMedium)
                        Text(
                            page.detail.tags.joinToString(", "),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                Text(page.detail.description, modifier = Modifier.padding(vertical = 12.dp))
                if (resume != null) {
                    Button(onClick = { onOpenChapter(resume.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Continue: Chapter ${resume.number}")
                    }
                }
                Text(
                    "Chapters",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            items(page.chapters.asReversed(), key = { it.id }) { chapter ->
                Column(Modifier.clickable { onOpenChapter(chapter.id) }.fillMaxWidth()) {
                    Text(
                        buildString {
                            append("Chapter ${chapter.number}")
                            if (chapter.title.isNotBlank()) append(" · ${chapter.title}")
                        },
                        modifier = Modifier.padding(vertical = 14.dp),
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
