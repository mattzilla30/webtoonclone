package com.dexter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dexter.data.SeriesSummary

/**
 * Series as rows of tiles that fit the screen width. [onLoadMore] runs once the last two rows are on screen,
 * a spinner shows while [loadingMore], and a button scrolls back to the top once the list is a few rows down.
 */
@Composable
fun SeriesGrid(series: List<SeriesSummary>, loadingMore: Boolean, onLoadMore: () -> Unit, onOpenSeries: (String) -> Unit) {
    val columns = adaptiveColumns(windowWidthDp())
    val rows = remember(series, columns) { series.chunked(columns) }
    val listState = rememberLazyListState()

    LaunchedEffect(listState, rows.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= rows.size - 2) onLoadMore() }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            items(rows, key = { it.first().id }) { pair ->
                Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { PickTile(it, { onOpenSeries(it.id) }, Modifier.weight(1f)) }
                    repeat(columns - pair.size) { Box(Modifier.weight(1f)) }
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
