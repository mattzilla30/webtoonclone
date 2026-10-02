package com.dexter.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexter.data.Chapter
import com.dexter.ui.LoadView
import com.dexter.ui.series.SeriesViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * One series on the TV: its title and a d-pad-navigable chapter list. Tapping a chapter opens it in
 * the reader.
 */
@Composable
fun TvSeriesScreen(
    seriesId: String,
    onOpenChapter: (chapterId: String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<SeriesViewModel> { parametersOf(seriesId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    LoadView(state, onRetry = viewModel::load) { page ->
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
            Text(
                page.detail.summary.title,
                style = MaterialTheme.typography.displaySmallEmphasized,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            page.detail.summary.author?.let { author ->
                Text(author, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${page.chapters.size} episodes",
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(page.chapters, key = { it.id }) { chapter ->
                    TvChapterRow(chapter = chapter, onClick = { onOpenChapter(chapter.id) })
                }
            }
        }
    }
}

/** One d-pad-focusable chapter row with a focus ring. */
@Composable
private fun TvChapterRow(chapter: Chapter, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .border(
                3.dp,
                if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Ep. ${chapter.number}",
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.padding(end = 16.dp),
        )
        Text(
            chapter.title.ifBlank { "Episode ${chapter.number}" },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
