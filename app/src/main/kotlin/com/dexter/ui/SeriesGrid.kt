package com.dexter.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dexter.R
import com.dexter.data.SeriesSummary

/**
 * Series as rows of tiles that fit the screen width, or as a list of rows with details when [asList].
 * [onLoadMore] runs once the end is near, a spinner shows while [loadingMore], and a button scrolls back to
 * the top once the list is a few rows down. A long press runs [onLongPress], such as subscribing.
 */
@Composable
fun SeriesGrid(
    series: List<SeriesSummary>,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    onOpenSeries: (String) -> Unit,
    asList: Boolean = false,
    subscribedIds: Set<String> = emptySet(),
    onLongPress: ((SeriesSummary) -> Unit)? = null,
) {
    val columns = if (asList) 1 else adaptiveColumns(windowWidthDp())
    val rows = remember(series, columns) { series.chunked(columns) }
    val listState = rememberLazyListState()

    LaunchedEffect(listState, rows.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= rows.size - 2) onLoadMore() }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            if (asList) {
                items(series, key = { it.id }, contentType = { "row" }) { item ->
                    SeriesListRow(item, subscribed = item.id in subscribedIds, onClick = { onOpenSeries(item.id) }, onLongClick = onLongPress?.let { { it(item) } })
                }
            } else {
                items(rows, key = { it.first().id }, contentType = { "tiles" }) { pair ->
                    Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { item ->
                            PickTile(
                                item,
                                { onOpenSeries(item.id) },
                                Modifier.weight(1f),
                                subscribed = item.id in subscribedIds,
                                onLongClick = onLongPress?.let { { it(item) } },
                            )
                        }
                        repeat(columns - pair.size) { Box(Modifier.weight(1f)) }
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

/** One result as a row: small cover, genre, title, author, year, and followers. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeriesListRow(series: SeriesSummary, subscribed: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(series.coverUrl, series.title, Modifier.width(56.dp).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.small), contentScale = ContentScale.Crop, thumb = true)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                GenreLabel(series.genre)
                Text(series.title, style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val details = listOfNotNull(series.author, series.year?.toString()).joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HeartCount(series.follows)
            }
            if (subscribed) {
                Icon(Icons.Default.Notifications, contentDescription = stringResource(R.string.subscribed), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
