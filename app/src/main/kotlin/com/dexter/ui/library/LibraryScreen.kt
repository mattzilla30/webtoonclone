package com.dexter.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexter.R
import com.dexter.data.LibraryData
import com.dexter.data.LibraryList
import com.dexter.data.QolPrefs
import com.dexter.data.ReadingListStore
import com.dexter.data.ReadingStatus
import com.dexter.data.SavedSeries
import com.dexter.data.SmartListStore
import com.dexter.data.newChapterEstimate
import com.dexter.data.offlineSeries
import com.dexter.ui.AppTopBar
import com.dexter.ui.ChoiceChip
import com.dexter.ui.TextPromptDialog
import com.dexter.ui.series.hasUnreadChapters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.seconds

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenSeries: (String) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val hiddenIds by viewModel.hiddenIds.collectAsStateWithLifecycle()
    val hidden by viewModel.hidden.collectAsStateWithLifecycle()
    val offlineOnly by viewModel.offlineOnly.collectAsStateWithLifecycle()
    val savedSeriesIds by viewModel.savedSeriesIds.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    var tabKey by rememberSaveable { mutableStateOf(LibraryList.Recent.key) }
    val tab = LibraryList.entries.firstOrNull { it.key == tabKey } ?: LibraryList.Recent
    val subscribedTab = tab == LibraryList.Subscribed
    var statusFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var collectionFilter by rememberSaveable { mutableStateOf<String?>(null) }
    // Saved library queries: tap one to filter, or save the current search as a new one.
    val smartLists: SmartListStore = koinInject()
    val allSmartLists by smartLists.all.collectAsStateWithLifecycle(initialValue = emptyList())
    val libraryScope = rememberCoroutineScope()
    var newSmartListName by remember { mutableStateOf<String?>(null) }
    // The curated cross-series reading lists, including the "Want to read" pile.
    val readingLists: ReadingListStore = koinInject()
    val allReadingLists by readingLists.all.collectAsStateWithLifecycle(initialValue = emptyList())
    var readingListFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val readingList = readingListFilter?.let { id -> allReadingLists.firstOrNull { it.id == id } }
    var query by rememberSaveable { mutableStateOf("") }
    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var tagging by remember { mutableStateOf(false) }
    var tagFilter by rememberSaveable { mutableStateOf(listOf<String>()) }
    val selected = remember { mutableStateSetOf<String>() }
    val sortMode = sortModeOf(library.librarySort, library.sortAlphabetical, library.sortUnreadFirst)
    // The author/genre/status/source query fields read the series details cached on the device.
    val queryMeta by viewModel.queryMeta.collectAsStateWithLifecycle()
    // Every tag you put on a series, for the filter chips.
    val allTags = remember(library.seriesTags) { library.seriesTags.values.flatten().distinct().sorted() }
    val collection = collectionFilter?.takeIf { tab == LibraryList.Lists && it in library.collections }
    // Every series in a list or collection, once each. Used for the "All" chip and its tab.
    val allListed = remember(library.lists, library.collections) { (library.lists + library.collections.values.flatten()).distinctBy { it.id } }
    val tabItems = remember(library, allListed, tab, collection, readingList, statusFilter, offlineOnly, savedSeriesIds) {
        val base = when {
            readingList != null -> readingList.entries.map { SavedSeries(it.seriesId, it.title, it.coverUrl) }
            collection != null -> library.collections.getValue(collection)
            tab == LibraryList.Lists && statusFilter == null -> allListed
            else -> listFor(library, tab)
        }
        offlineSeries(base, savedSeriesIds)
    }
    val lastReadById = remember(library.recent) { HashMap<String, String?>().also { map -> library.recent.forEach { map.putIfAbsent(it.id, it.chapterNumber) } } }
    val recentById = remember(library.recent) { library.recent.associateBy { it.id } }
    // Spoiler-safe blur: covers stay blurred until the series is started.
    val context = LocalContext.current
    val qolPrefs = remember(context) { QolPrefs(context) }
    val spoilerBlur by qolPrefs.spoilerBlur.collectAsStateWithLifecycle(initialValue = false)
    val subscribedById = remember(library.subscribed) { library.subscribed.associateBy { it.id } }
    // Warm the query metadata for what is on screen, so author:/genre:/status:/source: keep working offline.
    LaunchedEffect(tabItems) { viewModel.warmQueryMeta(tabItems.map { it.id }) }
    val items = remember(library, tabItems, tab, collection, statusFilter, query, unreadOnly, sortMode, tagFilter, queryMeta) {
        val knownOf = { series: SavedSeries -> subscribedById[series.id] ?: series }
        val unread = { series: SavedSeries -> hasUnreadChapters(knownOf(series).knownChapterNumber, lastReadById[series.id]) }
        sortSaved(
            filterSaved(
                if (tab == LibraryList.Lists && collection == null) tabItems.filter { statusFilter == null || it.status?.name == statusFilter } else tabItems,
                query,
                unreadOnly && subscribedTab,
                unread,
                tagFilter = tagFilter.toSet(),
                seriesTags = { library.seriesTags[it.id].orEmpty() },
                meta = { queryMeta[it.id] },
            ),
            sortMode,
            hasUnread = unread,
            unreadCount = { series ->
                newChapterEstimate(knownOf(series).knownChapterNumber, lastReadById[series.id]) ?: if (unread(series)) 1 else 0
            },
            lastReadAt = { series -> recentById[series.id]?.at ?: series.at },
            addedAt = library.addedAt,
        )
    }
    val selecting = selected.isNotEmpty()
    val selectedSeries = items.filter { it.id in selected }

    var undo by remember { mutableStateOf<UndoState?>(null) }
    val remove: (List<SavedSeries>) -> Unit = { removed ->
        undo = UndoState(tab, tabItems, removed.size, collection)
        val ids = removed.mapTo(HashSet()) { it.id }
        if (collection != null) viewModel.removeFromCollection(collection, ids) else viewModel.delete(tab, ids)
        selected.clear()
    }

    renaming?.let { old ->
        TextPromptDialog(
            title = "Rename collection",
            initial = old,
            confirmLabel = "Rename",
            onConfirm = { name ->
                viewModel.renameCollection(old, name)
                collectionFilter = name.trim()
            },
            onDismiss = { renaming = null },
        )
    }
    hidden?.let { state ->
        HiddenSeriesDialog(state, onOpen = onOpenSeries, onUnhide = viewModel::unhide, onDismiss = viewModel::closeHidden)
    }
    if (tagging && selecting) {
        val ids = selectedSeries.map { it.id }
        val existing = ids.flatMap { library.seriesTags[it].orEmpty() }.toSet()
        TagEditorDialog(
            count = selectedSeries.size,
            existing = existing,
            suggestions = allTags.filter { it !in existing },
            onAdd = { viewModel.addTag(selectedSeries, it) },
            onRemove = { viewModel.removeTag(selectedSeries, it) },
            onDismiss = { tagging = false },
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(stringResource(R.string.my_series))
            LibraryTabs(tab) { list ->
                tabKey = list.key
                selected.clear()
            }
            if (tab == LibraryList.Lists) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChoiceChip("All ${allListed.size}", statusFilter == null && collection == null && readingList == null) { statusFilter = null; collectionFilter = null; readingListFilter = null }
                    ReadingStatus.entries.forEach { status ->
                        ChoiceChip("${status.label} ${library.lists.count { it.status == status }}", collection == null && readingList == null && statusFilter == status.name) { statusFilter = status.name; collectionFilter = null; readingListFilter = null }
                    }
                    library.collections.keys.sorted().forEach { name ->
                        ChoiceChip("$name ${library.collections[name].orEmpty().size}", collection == name) { collectionFilter = name; statusFilter = null; readingListFilter = null }
                    }
                    allReadingLists.forEach { list ->
                        ChoiceChip("📚 ${list.name} ${list.entries.size}", readingList?.id == list.id) { readingListFilter = list.id; statusFilter = null; collectionFilter = null }
                    }
                    if (hiddenIds.isNotEmpty()) {
                        ChoiceChip("Hidden ${hiddenIds.size}", false) { viewModel.openHidden() }
                    }
                }
                if (collection != null) {
                    Row(Modifier.padding(horizontal = 8.dp)) {
                        TextButton(onClick = { renaming = collection }) { Text("Rename") }
                        TextButton(
                            onClick = {
                                viewModel.deleteCollection(collection)
                                collectionFilter = null
                            },
                        ) { Text("Delete this collection") }
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.filter_by_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            // Smart lists: saved library queries. Tap one to apply it; save the current search as one.
            if (allSmartLists.isNotEmpty() || query.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    allSmartLists.forEach { list ->
                        ChoiceChip(list.name, query == list.queryText) { query = list.queryText }
                    }
                    if (query.isNotBlank()) {
                        TextButton(onClick = { newSmartListName = "" }) { Text("Save") }
                    }
                    allSmartLists.firstOrNull { it.queryText == query }?.let { applied ->
                        TextButton(
                            onClick = {
                                libraryScope.launch { smartLists.delete(applied.name) }
                                query = ""
                            },
                        ) { Text("Delete") }
                    }
                }
            }
            newSmartListName?.let {
                TextPromptDialog(
                    title = "Save smart list",
                    initial = "",
                    confirmLabel = "Save",
                    onConfirm = { name ->
                        libraryScope.launch { smartLists.save(name, query) }
                        newSmartListName = null
                    },
                    onDismiss = { newSmartListName = null },
                )
            }
            // Your own tags narrow the list, together with the search box above.
            TagFilterRow(allTags, tagFilter.toSet()) { tag ->
                tagFilter = if (tag in tagFilter) tagFilter - tag else tagFilter + tag
            }
            if (subscribedTab) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Unread only", unreadOnly) { unreadOnly = !unreadOnly }
                    if (unreadSeriesCount(library) > 0) {
                        TextButton(onClick = viewModel::markAllRead) { Text("Mark all read") }
                    }
                }
            }
            if (selecting) {
                SelectionBar(
                    count = selected.size,
                    collections = library.collections.keys.sorted(),
                    onSelectAll = { selected.addAll(items.map { it.id }) },
                    onClear = { selected.clear() },
                    onDelete = { remove(selectedSeries) },
                    onStatus = { status ->
                        viewModel.setStatus(selectedSeries, status)
                        selected.clear()
                    },
                    onCollection = { name ->
                        viewModel.addToCollection(selectedSeries, name)
                        selected.clear()
                    },
                    onDownload = {
                        viewModel.downloadUnread(selectedSeries)
                        selected.clear()
                    },
                    onMarkRead = {
                        viewModel.markReadAll(selectedSeries)
                        selected.clear()
                    },
                    onMarkUnread = {
                        viewModel.markUnreadAll(selectedSeries)
                        selected.clear()
                    },
                    onTag = { tagging = true },
                    onHide = {
                        viewModel.hideSeries(selectedSeries)
                        selected.clear()
                    },
                    onDeleteDownloads = {
                        viewModel.deleteSeriesDownloads(selectedSeries)
                        selected.clear()
                    },
                )
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${items.size} series", style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 48dp touch targets, as in the selection bar.
                        val touch = Modifier.minimumInteractiveComponentSize()
                        Box {
                            TextButton(onClick = { sortMenu = true }, modifier = touch) { Text("Sort: ${sortMode.label}") }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                LibrarySort.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(if (mode == sortMode) "✓ ${mode.label}" else mode.label) },
                                        onClick = {
                                            sortMenu = false
                                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                            viewModel.setSort(mode)
                                        },
                                    )
                                }
                            }
                        }
                        TextButton(onClick = { viewModel.setGrid(!library.libraryGrid) }, modifier = touch) { Text(if (library.libraryGrid) "Rows" else "Grid") }
                        TextButton(enabled = items.isNotEmpty(), onClick = { remove(items) }, modifier = touch) { Text("Delete all") }
                    }
                }
            }
            if (items.isEmpty()) {
                LibraryEmpty(
                    tab = tab,
                    filtering = query.isNotBlank() || unreadOnly || statusFilter != null || collection != null || tagFilter.isNotEmpty(),
                    onClearFilters = {
                        query = ""
                        unreadOnly = false
                        statusFilter = null
                        collectionFilter = null
                        tagFilter = emptyList()
                    },
                    onOpenSearch = onOpenSearch,
                )
            } else {
                val toggle: (SavedSeries, Boolean) -> Unit = { series, on ->
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    if (on) selected.add(series.id) else selected.remove(series.id)
                }
                if (library.libraryGrid) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(110.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items, key = { it.id }) { series ->
                            val known = subscribedById[series.id] ?: series
                            LibraryTile(
                                series = series,
                                newLabel = if (hasUnreadChapters(known.knownChapterNumber, lastReadById[series.id])) newChapterEstimate(known.knownChapterNumber, lastReadById[series.id])?.let { "$it new" } ?: "New" else null,
                                selected = series.id in selected,
                                selecting = selecting,
                                onOpen = { onOpenSeries(series.id) },
                                onSelect = { toggle(series, it) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(items, key = { it.id }) { series ->
                            val known = subscribedById[series.id]
                            val unread = known != null && known.knownChapterId != null && hasUnreadChapters(known.knownChapterNumber, lastReadById[series.id])
                            // Swipe right to mark read (subscriptions with new chapters), left to remove from this list.
                            SwipeRow(
                                startLabel = if (unread) "Mark read" else null,
                                endLabel = "Remove",
                                enabled = !selecting,
                                onStart = { viewModel.markRead(series) },
                                onEnd = { remove(listOf(series)) },
                                modifier = Modifier.animateItem(),
                            ) {
                                LibraryRow(
                                    series = series,
                                    tab = tab,
                                    showNew = subscribedTab && hasUnreadChapters(series.knownChapterNumber, lastReadById[series.id]),
                                    selected = series.id in selected,
                                    selecting = selecting,
                                    newCount = newChapterEstimate(series.knownChapterNumber, lastReadById[series.id]),
                                    blurCover = spoilerBlur && series.id !in recentById,
                                    onOpen = { onOpenSeries(series.id) },
                                    onSelect = { toggle(series, it) },
                                )
                            }
                        }
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
        if (undo == null) {
            toast?.let { message ->
                LaunchedEffect(message) {
                    delay(3.seconds)
                    viewModel.clearToast()
                }
                Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Text(message) }
            }
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
