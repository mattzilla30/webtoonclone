package com.webtoonclone.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webtoonclone.ui.LoadView
import com.webtoonclone.ui.PickTile
import com.webtoonclone.ui.RankRow

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    initialGenre: String?,
    onOpenSeries: (String) -> Unit,
) {
    val results by viewModel.results.collectAsState()
    val recent by viewModel.recentSearches.collectAsState()
    val trending by viewModel.trending.collectAsState()
    var text by rememberSaveable { mutableStateOf(viewModel.query) }

    androidx.compose.runtime.LaunchedEffect(initialGenre) {
        if (initialGenre != null) {
            text = initialGenre
            viewModel.openGenre(initialGenre)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Search series") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (text.isNotEmpty()) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp).clickable {
                            text = ""
                            viewModel.clear()
                        })
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search(text) }),
                modifier = Modifier.weight(1f),
            )
            if (results != null) {
                TextButton(onClick = { text = ""; viewModel.clear() }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        val current = results
        if (current == null) {
            Idle(recent, trending, viewModel, onOpenSeries) { genre ->
                text = genre
                viewModel.openGenre(genre)
            }
        } else {
            LoadView(current, onRetry = { viewModel.search(text) }) { series ->
                if (series.isEmpty()) {
                    Text("No series found.", modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(series.chunked(2).size) { row ->
                            Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val pair = series.chunked(2)[row]
                                pair.forEach { PickTile(it, { onOpenSeries(it.id) }, Modifier.weight(1f)) }
                                if (pair.size == 1) Box(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Idle(
    recent: List<String>,
    trending: List<com.webtoonclone.data.SeriesSummary>,
    viewModel: SearchViewModel,
    onOpenSeries: (String) -> Unit,
    onGenre: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (recent.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent Searches", fontWeight = FontWeight.Bold)
                    Text("Delete all", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { viewModel.clearSearches() })
                }
                FlowRow(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEach { term ->
                        Row(
                            Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { viewModel.search(term) }.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(term, fontSize = 12.sp)
                            Icon(Icons.Default.Clear, contentDescription = "Remove", modifier = Modifier.padding(start = 6.dp).size(12.dp).clickable { viewModel.removeSearch(term) })
                        }
                    }
                }
            }
        }
        if (trending.isNotEmpty()) {
            item { Text("Trending Series", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)) }
            items(3) { i ->
                Row {
                    RankRow(i + 1, trending[i], { onOpenSeries(trending[i].id) }, Modifier.weight(1f))
                    trending.getOrNull(i + 3)?.let { RankRow(i + 4, it, { onOpenSeries(it.id) }, Modifier.weight(1f)) }
                }
            }
        }
        item { Text("Favorite Genres", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp)) }
        items(SearchGenres.chunked(4).size) { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val cells = SearchGenres.chunked(4)[row]
                cells.forEach { (name, icon) ->
                    Column(Modifier.weight(1f).clickable { onGenre(name) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Text(icon, fontSize = 22.sp)
                        }
                        Text(name, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
                    }
                }
                repeat(4 - cells.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}
