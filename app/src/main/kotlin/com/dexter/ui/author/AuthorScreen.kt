package com.dexter.ui.author

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexter.R
import com.dexter.ui.LoadView
import com.dexter.ui.PickTile
import com.dexter.ui.adaptiveColumns
import com.dexter.ui.iconTap
import com.dexter.ui.theme.Green

@Composable
fun AuthorScreen(viewModel: AuthorViewModel, name: String, onBack: () -> Unit, onOpenSeries: (String) -> Unit) {
    val state by viewModel.state.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState()
    val following by viewModel.following.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), modifier = Modifier.iconTap(onBack))
            Text(name.ifBlank { "Author" }, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f).padding(start = 16.dp))
            Text(
                if (following) "Following" else "+ Follow",
                color = if (following) Color.Black else Green,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .then(if (following) Modifier.background(Green) else Modifier.border(1.dp, Green, RoundedCornerShape(14.dp)))
                    .clickable { viewModel.toggleFollow(name) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
        LoadView(state, onRetry = viewModel::load) { series ->
            if (series.isEmpty()) {
                Text(stringResource(R.string.no_series_found), modifier = Modifier.padding(16.dp))
            } else {
                val columns = adaptiveColumns(LocalConfiguration.current.screenWidthDp.toFloat())
                val rows = remember(series, columns) { series.chunked(columns) }
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
                            repeat(columns - pair.size) { Box(Modifier.weight(1f)) }
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
