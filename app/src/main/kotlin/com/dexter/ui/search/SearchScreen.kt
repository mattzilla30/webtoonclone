package com.dexter.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexter.R
import com.dexter.data.ContentTags
import com.dexter.data.Formats
import com.dexter.data.Genres
import com.dexter.data.Order
import com.dexter.data.SavedSearch
import com.dexter.data.SeriesSummary
import com.dexter.data.SuggestiveTags
import com.dexter.data.Themes
import com.dexter.ui.ChoiceChip
import com.dexter.ui.Cover
import com.dexter.ui.GenreLabel
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
    val results by viewModel.results.collectAsState()
    val recent by viewModel.recentSearches.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val message by viewModel.message.collectAsState()
    val offlineSavedAt by viewModel.offlineSavedAt.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val savedSearches by viewModel.savedSearches.collectAsState()
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
                    when (name) {
                        "Random" -> viewModel.openRandom(onOpenSeries)
                        "Recently added" -> { text = name; viewModel.openBrowse(name, Order.Newest) }
                        "Top rated" -> { text = name; viewModel.openBrowse(name, Order.TopRated) }
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
            if (viewModel.canSave) {
                Text(
                    "Save this search",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).clickable { saveName = text.ifBlank { "" } },
                )
            }
            LoadView(current, onRetry = { viewModel.search(text) }) { series ->
                if (series.isEmpty()) {
                    Text(stringResource(R.string.no_series_found), modifier = Modifier.padding(16.dp))
                } else {
                    val columns = adaptiveColumns(windowWidthDp())
                    val rows = remember(series, columns) { series.chunked(columns) }
                    val listState = rememberLazyListState()

                    // Load the next page once the last two rows are on screen.
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
                                    LoadingIndicator(Modifier.size(40.dp))
                                }
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
    saved: List<SavedSearch>,
    suggestions: List<SeriesSummary>,
    message: String?,
    viewModel: SearchViewModel,
    onOpenSeries: (String) -> Unit,
    onBrowse: (String) -> Unit,
    onTag: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (message != null) {
            item { Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)) }
        }
        // Titles that match what is being typed, before the browse lists.
        items(suggestions, key = { it.id }) { series ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpenSeries(series.id) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(series.coverUrl, series.title, Modifier.width(36.dp).aspectRatio(2f / 3f), thumb = true)
                Column(Modifier.padding(start = 12.dp)) {
                    GenreLabel(series.genre)
                    Text(series.title, style = MaterialTheme.typography.titleSmallEmphasized)
                }
            }
        }
        if (saved.isNotEmpty()) {
            item {
                Text(stringResource(R.string.saved_searches), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    saved.forEach { search ->
                        Row(
                            Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { viewModel.openSaved(search) }.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(search.name, style = MaterialTheme.typography.bodySmall)
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.remove), modifier = Modifier.padding(start = 6.dp).size(12.dp).clickable { viewModel.deleteSaved(search.name) })
                        }
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.recent_searches), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.delete_all), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { viewModel.clearSearches() })
                }
                FlowRow(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.sortedBy { it.lowercase() }.forEach { term ->
                        Row(
                            Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { viewModel.search(term) }.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(term, style = MaterialTheme.typography.bodySmall)
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.remove), modifier = Modifier.padding(start = 6.dp).size(12.dp).clickable { viewModel.removeSearch(term) })
                        }
                    }
                }
            }
        }
        // Sections and their tags are in alphabetical order.
        tagSection("Browse", BrowseOptions, onBrowse)
        tagSection("Content", ContentTags, onTag)
        tagSection("Formats", Formats, onTag)
        tagSection("Genres", Genres.map { it.name }, onTag)
        tagSection("Suggestive", SuggestiveTags, onTag)
        tagSection("Themes", Themes, onTag)
        item { Box(Modifier.padding(bottom = 24.dp)) }
    }
}

/** A titled group of tappable tag chips. */
@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.tagSection(title: String, tags: List<String>, onTag: (String) -> Unit) {
    item {
        Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.sortedBy { it.lowercase() }.forEach { tag ->
                Text(
                    tag,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onTag(tag) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private val sortLabels = listOf(
    Order.Popular to "Popular",
    Order.Newest to "Newest",
    Order.Updated to "Recently updated",
    Order.TopRated to "Top rated",
)

/** Lists that need no search. Random opens one series, and the others list every series in an order. */
private val BrowseOptions = listOf("Random", "Recently added", "Top rated")

/** Chips that choose how search results are ordered. */
@Composable
private fun SortRow(selected: Order, onSelect: (Order) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        sortLabels.forEach { (order, label) -> ChoiceChip(label, order == selected) { onSelect(order) } }
    }
}
