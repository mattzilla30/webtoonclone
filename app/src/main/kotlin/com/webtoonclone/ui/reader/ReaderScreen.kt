package com.webtoonclone.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.webtoonclone.ui.LoadView
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onOpenChapter: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LoadView(state, onRetry = viewModel::load) { page ->
        val listState = rememberLazyListState()

        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { viewModel.saveProgress(it) }
        }

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { page.prevId?.let(onOpenChapter) }, enabled = page.prevId != null) {
                    Text("← Prev")
                }
                TextButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text("Chapter ${page.chapter.number}")
                }
                TextButton(onClick = { page.nextId?.let(onOpenChapter) }, enabled = page.nextId != null) {
                    Text("Next →")
                }
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(page.pages, key = { _, url -> url }) { index, url ->
                    SubcomposeAsyncImage(
                        model = url,
                        contentDescription = "Page ${index + 1}",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                        loading = {
                            Box(
                                Modifier.fillMaxWidth().height(500.dp)
                                    .background(MaterialTheme.colorScheme.surface),
                            )
                        },
                        error = {
                            Text("Page ${index + 1} failed to load", modifier = Modifier.padding(16.dp))
                        },
                        success = { SubcomposeAsyncImageContent() },
                    )
                }
            }
        }
    }
}
