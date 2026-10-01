package com.dexter.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexter.R
import com.dexter.data.LibraryData
import com.dexter.data.LibraryList
import com.dexter.data.ReadingStatus
import com.dexter.data.SavedSeries
import com.dexter.data.newChapterEstimate
import com.dexter.ui.AppTopBar
import com.dexter.ui.ChoiceChip
import com.dexter.ui.series.hasUnreadChapters
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenSeries: (String) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    var tabKey by rememberSaveable { mutableStateOf(LibraryList.Recent.key) }
    val tab = LibraryList.entries.firstOrNull { it.key == tabKey } ?: LibraryList.Recent
    val subscribedTab = tab == LibraryList.Subscribed
    var statusFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var collectionFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    val selected = remember { mutableStateSetOf<String>() }
    val sortMode = sortModeOf(library.sortAlphabetical, library.sortUnreadFirst)
    val collection = collectionFilter?.takeIf { tab == LibraryList.Lists && it in library.collections }
    // Every series in a list or collection, once each. Used for the "All" chip and its tab.
    val allListed = remember(library.lists, library.collections) { (library.lists + library.collections.values.flatten()).distinctBy { it.id } }
    val tabItems = remember(library, allListed, tab, collection, statusFilter) {
        when {
            collection != null -> library.collections.getValue(collection)
            tab == LibraryList.Lists && statusFilter == null -> allListed
            else -> listFor(library, tab)
        }
    }
    val lastReadById = remember(library.recent) { HashMap<String, String?>().also { map -> library.recent.forEach { map.putIfAbsent(it.id, it.chapterNumber) } } }
    val items = remember(library, tabItems, tab, collection, statusFilter, query, unreadOnly, sortMode) {
        val unread = { series: SavedSeries -> hasUnreadChapters(series.knownChapterNumber, lastReadById[series.id]) }
        sortSaved(
            filterSaved(
                if (tab == LibraryList.Lists && collection == null) tabItems.filter { statusFilter == null || it.status?.name == statusFilter } else tabItems,
                query,
                unreadOnly && subscribedTab,
                unread,
            ),
            sortMode,
            hasUnread = unread,
        )
    }

    var undo by remember { mutableStateOf<UndoState?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                stringResource(R.string.my_series),
                actions = {
                    FilledTonalIconToggleButton(
                        checked = library.notificationsEnabled,
                        onCheckedChange = { viewModel.setNotifications(it) },
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = if (library.notificationsEnabled) "Notifications on" else "Notifications off",
                        )
                    }
                    if (items.size > 1) {
                        IconButton(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onOpenSeries(items.random().id)
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Pick one at random")
                        }
                    }
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                    }
                },
            )
            LibraryTabs(tab) { list ->
                tabKey = list.key
                selected.clear()
            }
            if (tab == LibraryList.Lists) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChoiceChip("All ${allListed.size}", statusFilter == null && collection == null) { statusFilter = null; collectionFilter = null }
                    ReadingStatus.entries.forEach { status ->
                        ChoiceChip("${status.label} ${library.lists.count { it.status == status }}", collection == null && statusFilter == status.name) { statusFilter = status.name; collectionFilter = null }
                    }
                    library.collections.keys.sorted().forEach { name ->
                        ChoiceChip("$name ${library.collections[name].orEmpty().size}", collection == name) { collectionFilter = name; statusFilter = null }
                    }
                }
                if (collection != null) {
                    TextButton(
                        onClick = {
                            viewModel.deleteCollection(collection)
                            collectionFilter = null
                        },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) { Text("Delete this collection") }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.filter_by_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (subscribedTab) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Unread only", unreadOnly) { unreadOnly = !unreadOnly }
                    if (unreadSeriesCount(library) > 0) {
                        TextButton(onClick = viewModel::markAllRead) { Text("Mark all read") }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${items.size} series", style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.primary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        viewModel.setSort(sortMode.next())
                    }) { Text(sortMode.label) }
                    TextButton(
                        enabled = selected.isNotEmpty(),
                        onClick = {
                            undo = UndoState(tab, tabItems, selected.size, collection)
                            if (collection != null) viewModel.removeFromCollection(collection, selected.toSet()) else viewModel.delete(tab, selected.toSet())
                            selected.clear()
                        },
                    ) { Text("Delete") }
                    TextButton(
                        enabled = items.isNotEmpty(),
                        onClick = {
                            undo = UndoState(tab, tabItems, items.size, collection)
                            val ids = items.map { it.id }.toSet()
                            if (collection != null) viewModel.removeFromCollection(collection, ids) else viewModel.delete(tab, ids)
                            selected.clear()
                        },
                    ) { Text("Delete all") }
                }
            }
            if (items.isEmpty()) {
                LibraryEmpty(
                    tab = tab,
                    filtering = query.isNotBlank() || unreadOnly || statusFilter != null || collection != null,
                    onClearFilters = {
                        query = ""
                        unreadOnly = false
                        statusFilter = null
                        collectionFilter = null
                    },
                    onOpenSearch = onOpenSearch,
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { series ->
                        LibraryRow(
                            series = series,
                            tab = tab,
                            showNew = subscribedTab && hasUnreadChapters(series.knownChapterNumber, lastReadById[series.id]),
                            selected = series.id in selected,
                            selecting = selected.isNotEmpty(),
                            newCount = newChapterEstimate(series.knownChapterNumber, lastReadById[series.id]),
                            onOpen = { onOpenSeries(series.id) },
                            onSelect = { on ->
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                if (on) selected.add(series.id) else selected.remove(series.id)
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }

        // Offer an undo for a few seconds after a removal.
        undo?.let { state ->
            LaunchedEffect(state) {
                delay(6.seconds)
                undo = null
            }
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = {
                    TextButton(
                        onClick = {
                            if (state.collection != null) viewModel.restoreCollection(state.collection, state.snapshot) else viewModel.restore(state.list, state.snapshot)
                            undo = null
                        },
                    ) { Text("Undo") }
                },
            ) { Text("Removed ${state.count} series") }
        }
    }
}

private fun listFor(library: LibraryData, tab: LibraryList): List<SavedSeries> = when (tab) {
    LibraryList.Recent -> library.recent
    LibraryList.Subscribed -> library.subscribed
    LibraryList.Lists -> library.lists
}

/** What an undo needs: which tab, the list as it was, and how many series were removed. */
private data class UndoState(val list: LibraryList, val snapshot: List<SavedSeries>, val count: Int, val collection: String? = null)
