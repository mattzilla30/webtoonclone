package com.webtoonclone.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.webtoonclone.ui.LoadView

@Composable
fun HomeScreen(viewModel: HomeViewModel, onOpenSeries: (String) -> Unit) {
    val state by viewModel.state.collectAsState()
    var text by rememberSaveable { mutableStateOf("") }

    Column(Modifier.padding(horizontal = 12.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Search series") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search(text) }),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        LoadView(state, onRetry = viewModel::load) { series ->
            if (series.isEmpty()) {
                Text("No series found.")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(130.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(series, key = { it.id }) { item ->
                        Column(Modifier.clickable { onOpenSeries(item.id) }) {
                            AsyncImage(
                                model = item.coverUrl,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                            )
                            Text(
                                item.title,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
