package com.dexter.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexter.R
import com.dexter.data.LibraryData
import com.dexter.data.LibraryList
import com.dexter.data.ReadingStatus
import com.dexter.data.SavedSeries
import com.dexter.ui.ChoiceChip
import com.dexter.ui.Cover
import com.dexter.ui.iconTap
import com.dexter.ui.series.hasUnreadChapters
import com.dexter.ui.theme.Green
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenSeries: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val library by viewModel.library.collectAsState()
    var tabKey by rememberSaveable { mutableStateOf(LibraryList.Recent.key) }
    val tab = LibraryList.entries.firstOrNull { it.key == tabKey } ?: LibraryList.Recent
    val subscribedTab = tab == LibraryList.Subscribed
    var statusFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateListOf<String>() }
    val alphabetical = library.sortAlphabetical
    val tabItems = listFor(library, tab)
    val items = sortSaved(
        if (tab == LibraryList.Lists) tabItems.filter { statusFilter == null || it.status?.name == statusFilter } else tabItems,
        alphabetical,
    )

    var undo by remember { mutableStateOf<UndoState?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.my_series), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (library.notificationsEnabled) "Notifications: On" else "Notifications: Off",
                        fontSize = 12.sp,
                        color = if (library.notificationsEnabled) Green else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { viewModel.setNotifications(!library.notificationsEnabled) },
                    )
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search), modifier = Modifier.iconTap(onOpenSearch))
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings), modifier = Modifier.iconTap(onOpenSettings))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Tab("RECENT", tab == LibraryList.Recent, Modifier.weight(1f)) { tabKey = LibraryList.Recent.key; selected.clear() }
                Tab("SUBSCRIBED", subscribedTab, Modifier.weight(1f)) { tabKey = LibraryList.Subscribed.key; selected.clear() }
                Tab("LISTS", tab == LibraryList.Lists, Modifier.weight(1f)) { tabKey = LibraryList.Lists.key; selected.clear() }
            }
            if (tab == LibraryList.Lists) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChoiceChip("All", statusFilter == null) { statusFilter = null }
                    ReadingStatus.entries.forEach { status ->
                        ChoiceChip(status.label, statusFilter == status.name) { statusFilter = status.name }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${items.size} SERIES", fontSize = 12.sp, color = Green, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        if (alphabetical) "Sort: A-Z" else "Sort: Recent",
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { viewModel.setSortAlphabetical(!alphabetical) },
                    )
                    Text(
                        "Delete", fontSize = 12.sp,
                        modifier = Modifier.clickable(enabled = selected.isNotEmpty()) {
                            undo = UndoState(tab, tabItems, selected.size)
                            viewModel.delete(tab, selected.toSet())
                            selected.clear()
                        },
                    )
                    Text(
                        "Delete All", fontSize = 12.sp,
                        modifier = Modifier.clickable(enabled = items.isNotEmpty()) {
                            undo = UndoState(tab, tabItems, items.size)
                            viewModel.delete(tab, items.map { it.id }.toSet())
                            selected.clear()
                        },
                    )
                }
            }
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        when (tab) {
                            LibraryList.Subscribed -> "Subscribe to a series to see it here."
                            LibraryList.Lists -> "Add a series to a list from its page."
                            LibraryList.Recent -> "Series you read show up here."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { series ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenSeries(series.id) }.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Cover(series.coverUrl, series.title, Modifier.width(40.dp).aspectRatio(2f / 3f))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                val lastRead = library.recent.firstOrNull { it.id == series.id }?.chapterNumber
                                if (subscribedTab && hasUnreadChapters(series.knownChapterNumber, lastRead)) {
                                    Text(stringResource(R.string.new_label), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Green)
                                }
                                Text(series.title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                if (tab == LibraryList.Lists) {
                                    series.status?.let { Text(it.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Green) }
                                }
                                series.chapterNumber?.let {
                                    Text("Ep. $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Checkbox(
                                checked = series.id in selected,
                                onCheckedChange = { if (it) selected.add(series.id) else selected.remove(series.id) },
                                colors = CheckboxDefaults.colors(checkedColor = Green),
                            )
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
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Removed ${state.count} series", fontSize = 13.sp)
                Text(
                    "Undo",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Green,
                    modifier = Modifier.clickable {
                        viewModel.restore(state.list, state.snapshot)
                        undo = null
                    },
                )
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
private data class UndoState(val list: LibraryList, val snapshot: List<SavedSeries>, val count: Int)

@Composable
private fun Tab(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(40.dp).background(if (active) Green else MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
