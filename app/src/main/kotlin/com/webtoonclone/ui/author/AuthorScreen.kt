package com.webtoonclone.ui.author

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webtoonclone.ui.LoadView
import com.webtoonclone.ui.PickTile

@Composable
fun AuthorScreen(viewModel: AuthorViewModel, name: String, onBack: () -> Unit, onOpenSeries: (String) -> Unit) {
    val state by viewModel.state.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.clickable(onClick = onBack))
            Text(name.ifBlank { "Author" }, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(start = 16.dp))
        }
        LoadView(state, onRetry = viewModel::load) { series ->
            if (series.isEmpty()) {
                Text("No series found.", modifier = Modifier.padding(16.dp))
            } else {
                val rows = remember(series) { series.chunked(2) }
                val listState = rememberLazyListState()
                LaunchedEffect(listState, rows.size) {
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                        .collect { last -> if (last >= rows.size - 2) viewModel.loadMore() }
                }
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(rows.size) { row ->
                        Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val pair = rows[row]
                            pair.forEach { PickTile(it, { onOpenSeries(it.id) }, Modifier.weight(1f)) }
                            if (pair.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                    if (loadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
