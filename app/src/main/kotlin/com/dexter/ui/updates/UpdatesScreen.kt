package com.dexter.ui.updates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexter.R
import com.dexter.ui.Cover
import com.dexter.ui.GenreLabel
import com.dexter.ui.LoadView
import com.dexter.ui.OfflineBanner
import com.dexter.ui.timeAgo

@Composable
fun UpdatesScreen(viewModel: UpdatesViewModel, onOpenSeries: (String) -> Unit) {
    val state by viewModel.state.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState()
    val offlineSavedAt by viewModel.offlineSavedAt.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.updates), fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(16.dp))
        LoadView(state, onRetry = viewModel::load) { entries ->
            val listState = rememberLazyListState()

            // Load the next page once the last few rows are on screen.
            LaunchedEffect(listState, entries.size) {
                snapshotFlow {
                    val info = listState.layoutInfo
                    (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 4
                }.collect { nearEnd -> if (nearEnd) viewModel.loadMore() }
            }

            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                offlineSavedAt?.let { savedAt ->
                    item { OfflineBanner(savedAt, "updates", onRetry = viewModel::load) }
                }
                items(entries, key = { it.series.id }) { entry ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenSeries(entry.series.id) }.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(entry.series.coverUrl, entry.series.title, Modifier.width(48.dp).aspectRatio(2f / 3f), thumb = true)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            GenreLabel(entry.series.genre)
                            Text(entry.series.title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(
                                "Ep. ${entry.chapterNumber}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(timeAgo(entry.publishedAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (loadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator(Modifier.size(40.dp))
                        }
                    }
                }
            }
        }
    }
}
