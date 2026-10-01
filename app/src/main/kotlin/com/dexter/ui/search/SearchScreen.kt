package com.dexter.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexter.R
import com.dexter.data.Order
import com.dexter.data.SearchFilters
import com.dexter.data.activeFilters
import com.dexter.ui.BackToTopButton
import com.dexter.ui.ChoiceChip
import com.dexter.ui.LoadView
import com.dexter.ui.OfflineBanner
import com.dexter.ui.PickTile
import com.dexter.ui.adaptiveColumns
import com.dexter.ui.windowWidthDp

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    initialGenre: String?,
    onOpenSeries: (String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val results by viewModel.results.collectAsStateWithLifecycle()
    val recent by viewModel.recentSearches.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val offlineSavedAt by viewModel.offlineSavedAt.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val savedSearches by viewModel.savedSearches.collectAsStateWithLifecycle()
    var saveName by rememberSaveable { mutableStateOf<String?>(null) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf(viewModel.query) }
    saveName?.let { name ->
        AlertDialog(
            onDismissRequest = { saveName = null },
            title = { Text(stringResource(R.string.save_this_search)) },
            text = { OutlinedTextField(value = name, onValueChange = { saveName = it }, singleLine = true, placeholder = { Text(stringResource(R.string.name)) }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { viewModel.saveCurrent(name); saveName = null }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { saveName = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showFilters) {
        FiltersDialog(filters, onApply = { viewModel.setFilters(it) }, onDismiss = { showFilters = false })
    }

    LaunchedEffect(initialGenre) {
        if (initialGenre != null) {
            text = initialGenre
            viewModel.openTag(initialGenre)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = text,
                onValueChange = {
                    text = it
                    viewModel.onTyping(it)
                },
                placeholder = { Text(stringResource(R.string.search_series)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (text.isNotEmpty()) {
                        Icon(
                            Icons.Default.Clear, contentDescription = stringResource(R.string.clear),
                            modifier = Modifier.size(18.dp).clickable {
                                text = ""
                                viewModel.onTyping("")
                                viewModel.clear()
                            },
                        )
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search(text) }),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showFilters = true }) {
                Text(if (filters.isEmpty) "Filters" else "Filters (${filters.activeCount})", color = MaterialTheme.colorScheme.primary)
            }
            if (results != null) {
                TextButton(onClick = { text = ""; viewModel.onTyping(""); viewModel.clear() }) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        val current = results
        if (current == null) {
            Idle(
                recent = recent,
                saved = savedSearches,
                suggestions = if (shouldSuggest(text)) suggestions else emptyList(),
                message = message,
                viewModel = viewModel,
                onOpenSeries = onOpenSeries,
                onBrowse = { name ->
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    when (name) {
                        "Random" -> viewModel.openRandom(onOpenSeries)
                        "Recently added" -> { text = name; viewModel.openBrowse(name, Order.Newest) }
                        "Top rated" -> { text = name; viewModel.openBrowse(name, Order.TopRated) }
                        "Completed" -> { text = name; viewModel.setFilters(SearchFilters(status = listOf("completed"))) }
                    }
                },
                onTag = { tag ->
                    text = tag
                    viewModel.openTag(tag)
                },
            )
        } else {
            offlineSavedAt?.let { OfflineBanner(it, "results", onRetry = viewModel::retry) }
            SortRow(sort, viewModel::setSort)
            if (!filters.isEmpty) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    activeFilters(filters).forEach { active ->
                        InputChip(
                            selected = true,
                            onClick = { viewModel.setFilters(active.without) },
                            label = { Text(active.label) },
                            trailingIcon = { Icon(Icons.Default.Clear, contentDescription = "Remove", modifier = Modifier.size(InputChipDefaults.IconSize)) },
                        )
                    }
                }
            }
            if (viewModel.canSave) {
                TextButton(onClick = { saveName = text.ifBlank { "" } }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Save this search") }
            }
            LoadView(current, onRetry = { viewModel.search(text) }) { series ->
                if (series.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.no_series_found), style = MaterialTheme.typography.titleMediumEmphasized)
                        Text(
                            if (filters.isEmpty) "Try a different spelling or fewer words." else "Your filters may be too narrow.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        if (!filters.isEmpty) {
                            FilledTonalButton(onClick = { viewModel.setFilters(SearchFilters()) }, modifier = Modifier.padding(top = 16.dp)) {
                                Text("Clear filters")
                            }
                        }
                    }
                } else {
                    val columns = adaptiveColumns(windowWidthDp())
                    val rows = remember(series, columns) { series.chunked(columns) }
                    val listState = rememberLazyListState()

                    // Load the next page once the last two rows are on screen.
                    LaunchedEffect(listState, rows.size) {
                        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                            .collect { last -> if (last >= rows.size - 2) viewModel.loadMore() }
                    }

                    Box(Modifier.fillMaxSize()) {
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
}

private val sortLabels = listOf(
    Order.Popular to "Popular",
    Order.Newest to "Newest",
    Order.Updated to "Updated",
    Order.TopRated to "Top rated",
)

/** Chips that choose how search results are ordered. */
@Composable
private fun SortRow(selected: Order, onSelect: (Order) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sortLabels.forEach { (order, label) -> ChoiceChip(label, order == selected) { onSelect(order) } }
    }
}
