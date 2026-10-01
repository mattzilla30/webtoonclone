package com.dexter.ui.updates

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexter.R
import com.dexter.ui.AppTopBar
import com.dexter.ui.BackToTopButton
import com.dexter.ui.ChoiceChip
import com.dexter.ui.Cover
import com.dexter.ui.GenreLabel
import com.dexter.ui.Load
import com.dexter.ui.LoadView
import com.dexter.ui.OfflineBanner
import com.dexter.ui.timeAgo

@Composable
fun UpdatesScreen(viewModel: UpdatesViewModel, onOpenSeries: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val offlineSavedAt by viewModel.offlineSavedAt.collectAsStateWithLifecycle()
    val subscribedIds by viewModel.subscribedIds.collectAsStateWithLifecycle()
    var subscribedOnly by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(stringResource(R.string.updates))
        PullToRefreshBox(isRefreshing = state is Load.Loading, onRefresh = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            viewModel.load()
        }, modifier = Modifier.fillMaxSize()) {
            LoadView(state, onRetry = viewModel::load) { allEntries ->
                val entries = remember(allEntries, subscribedOnly, subscribedIds) {
                    if (subscribedOnly) allEntries.filter { it.series.id in subscribedIds } else allEntries
                }
                val listState = rememberLazyListState()

                // Load the next page once the last few rows are on screen.
                LaunchedEffect(listState, entries.size) {
                    snapshotFlow {
                        val info = listState.layoutInfo
                        (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 4
                    }.collect { nearEnd -> if (nearEnd) viewModel.loadMore() }
                }

                Box(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), state = listState) {
                        offlineSavedAt?.let { savedAt ->
                            item { OfflineBanner(savedAt, "updates", onRetry = viewModel::load) }
                        }
                        if (subscribedIds.isNotEmpty()) {
                            item {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                    ChoiceChip("Subscribed only", subscribedOnly) { subscribedOnly = !subscribedOnly }
                                }
                            }
                        }
                        if (subscribedOnly && entries.isEmpty() && !loadingMore) {
                            item {
                                Text(
                                    "None of the latest updates are from series you subscribed to.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
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
                                    Column(horizontalAlignment = Alignment.End) {
                                        if (entry.series.id in subscribedIds) {
                                            Icon(
                                                Icons.Default.Notifications,
                                                contentDescription = stringResource(R.string.subscribed),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        Text(timeAgo(entry.publishedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
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
                    BackToTopButton(listState, Modifier.align(Alignment.BottomEnd))
                }
            }
        }
    }
}
