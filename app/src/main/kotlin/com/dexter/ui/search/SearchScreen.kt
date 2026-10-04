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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexter.R
import com.dexter.data.Order
import com.dexter.data.QolPrefs
import com.dexter.data.SearchFilters
import com.dexter.data.activeFilters
import com.dexter.ui.ChoiceChip
import com.dexter.ui.LoadView
import com.dexter.ui.OfflineBanner
import com.dexter.ui.SeriesGrid
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    initialGenre: String?,
    initialBrowse: String? = null,
    focusOnOpen: Boolean = false,
    onBack: () -> Unit = {},
    onOpenSeries: (String) -> Unit,
    onOpenAuthor: (id: String, name: String) -> Unit = { _, _ -> },
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
    val authors by viewModel.authorSuggestions.collectAsStateWithLifecycle()
    val asList by viewModel.asList.collectAsStateWithLifecycle()
    val subscribedIds by viewModel.subscribedIds.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    var saveName by rememberSaveable { mutableStateOf<String?>(null) }
    var saveNotify by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf(viewModel.query) }
    // Opened from the Home search bar, the box takes focus once so the keyboard comes up ready to type.
    val focus = remember { FocusRequester() }
    var focused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (focusOnOpen && !focused) {
            focused = true
            focus.requestFocus()
        }
    }
    // The ViewModel can start a search on its own (saved search, filter change): mirror its query
    // into the field. Typing flows the other way, so this never fights the keyboard.
    val vmQuery by viewModel.queryFlow.collectAsStateWithLifecycle()
    // Only a query the ViewModel has not handed over yet replaces the field, so rotating the phone keeps
    // text you typed but have not searched.
    var mirrored by rememberSaveable { mutableStateOf(vmQuery) }
    LaunchedEffect(vmQuery) {
        if (vmQuery != mirrored) {
            mirrored = vmQuery
            text = vmQuery
        }
    }
    saveName?.let { name ->
        AlertDialog(
            onDismissRequest = { saveName = null },
            title = { Text(stringResource(R.string.save_this_search)) },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { saveName = it }, singleLine = true, placeholder = { Text(stringResource(R.string.name)) })
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp).toggleable(value = saveNotify, role = Role.Checkbox, onValueChange = { saveNotify = it }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = saveNotify, onCheckedChange = null)
                        Text("Notify me about new matches", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { viewModel.saveCurrent(name, saveNotify); saveName = null; saveNotify = false }) { Text(stringResource(R.string.save)) }
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
    // "See all" on Home opens a whole list in one order.
    LaunchedEffect(initialBrowse) {
        val order = browseOrders[initialBrowse] ?: return@LaunchedEffect
        text = initialBrowse.orEmpty()
        viewModel.openBrowse(text, order)
    }

    toast?.let { message ->
        LaunchedEffect(message) {
            delay(3.seconds)
            viewModel.clearToast()
        }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
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
                                // A 42dp target around the 18dp glyph, so it is tappable without fat-fingering.
                                modifier = Modifier.clickable {
                                    text = ""
                                    viewModel.onTyping("")
                                    viewModel.clear()
                                }.padding(12.dp).size(18.dp),
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
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                // An icon with a count badge; the clear button inside the box already resets the search.
                IconButton(onClick = { showFilters = true }) {
                    BadgedBox(badge = { if (!filters.isEmpty) Badge { Text("${filters.activeCount}") } }) {
                        Icon(Icons.Default.FilterList, contentDescription = if (filters.isEmpty) "Filters" else "Filters, ${filters.activeCount} on")
                    }
                }
            }

            val current = results
            if (current == null) {
                Idle(
                    recent = recent,
                    saved = savedSearches,
                    suggestions = if (shouldSuggest(text)) suggestions else emptyList(),
                    authors = if (shouldSuggest(text)) authors else emptyList(),
                    message = message,
                    viewModel = viewModel,
                    onOpenSeries = onOpenSeries,
                    onOpenAuthor = onOpenAuthor,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SortRow(sort, viewModel::setSort) }
                    TextButton(onClick = { viewModel.setAsList(!asList) }) { Text(if (asList) "Grid" else "List") }
                }
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
                        SeriesGrid(
                            series,
                            loadingMore,
                            onLoadMore = viewModel::loadMore,
                            onOpenSeries = onOpenSeries,
                            asList = asList,
                            subscribedIds = subscribedIds,
                            onLongPress = { item ->
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.toggleSubscribe(item)
                            },
                        )
                    }
                }
            }
        }
        toast?.let { message ->
            Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Text(message) }
        }
    }
}

/** The lists "See all" can open, by the label they show. */
private val browseOrders = mapOf(
    "Recently added" to Order.Newest,
    "Popular" to Order.Popular,
    "Top rated" to Order.TopRated,
    "Latest updates" to Order.Updated,
)

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
