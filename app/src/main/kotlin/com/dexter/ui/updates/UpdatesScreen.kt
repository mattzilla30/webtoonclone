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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexter.R
import com.dexter.ui.AppTopBar
import com.dexter.ui.Cover
import com.dexter.ui.GenreLabel
import com.dexter.ui.Load
import com.dexter.ui.LoadView
import com.dexter.ui.OfflineBanner
import com.dexter.ui.timeAgo

@Composable
fun UpdatesScreen(viewModel: UpdatesViewModel, onOpenSeries: (String) -> Unit) {
    val state by viewModel.state.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState()
    val offlineSavedAt by viewModel.offlineSavedAt.collectAsState()

    Column(Modifier.fillMaxSize()) {
        AppTopBar(stringResource(R.string.updates))
        PullToRefreshBox(isRefreshing = state is Load.Loading, onRefresh = viewModel::load, modifier = Modifier.fillMaxSize()) {
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
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            onClick = { onOpenSeries(entry.series.id) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Cover(
                                    entry.series.coverUrl,
                                    entry.series.title,
                                    Modifier.width(52.dp).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.small),
                                    contentScale = ContentScale.Crop,
                                    thumb = true,
                                )
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    GenreLabel(entry.series.genre)
                                    Text(entry.series.title, style = MaterialTheme.typography.titleSmallEmphasized)
                                    Text(
                                        "Ep. ${entry.chapterNumber}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(timeAgo(entry.publishedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
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
}
