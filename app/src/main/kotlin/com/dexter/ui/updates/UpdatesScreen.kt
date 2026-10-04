package com.dexter.ui.updates

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexter.R
import com.dexter.data.UpdateEntry
import com.dexter.ui.AppTopBar
import com.dexter.ui.BackToTopButton
import com.dexter.ui.ChoiceChip
import com.dexter.ui.Cover
import com.dexter.ui.GenreLabel
import com.dexter.ui.Load
import com.dexter.ui.LoadView
import com.dexter.ui.OfflineBanner
import com.dexter.ui.dayHeading
import com.dexter.ui.discover.ScheduleList
import com.dexter.ui.timeAgo
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/** A row in the Updates list: a day heading, or one update. */
private sealed interface UpdateRow {
    data class Heading(val label: String) : UpdateRow

    data class Entry(val entry: UpdateEntry) : UpdateRow
}

/** Puts a heading before each new day, in the order the list comes in. */
private fun withDayHeadings(entries: List<UpdateEntry>): List<UpdateRow> {
    val rows = ArrayList<UpdateRow>(entries.size + 8)
    var last: String? = null
    for (entry in entries) {
        val label = dayHeading(entry.publishedAt)
        if (label != last) {
            rows += UpdateRow.Heading(label)
            last = label
        }
        rows += UpdateRow.Entry(entry)
    }
    return rows
}

@Composable
fun UpdatesScreen(viewModel: UpdatesViewModel, onClose: () -> Unit, onOpenSeries: (String) -> Unit, onOpenChapter: (seriesId: String, chapterId: String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val allState by viewModel.state.collectAsStateWithLifecycle()
    val subscribedState by viewModel.subscribedState.collectAsStateWithLifecycle()
    val subscribedOnly by viewModel.subscribedOnly.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val offlineSavedAt by viewModel.offlineSavedAt.collectAsStateWithLifecycle()
    val subscribedIds by viewModel.subscribedIds.collectAsStateWithLifecycle()
    val lastRead by viewModel.lastRead.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    var showSchedule by remember { mutableStateOf(false) }
    val state = if (subscribedOnly) subscribedState else allState

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                stringResource(R.string.updates),
                actions = {
                    // The two views sit in the bar as toggles instead of a row of chips under it.
                    if (subscribedIds.isNotEmpty()) {
                        IconToggleButton(
                            checked = subscribedOnly && !showSchedule,
                            onCheckedChange = {
                                viewModel.setSubscribedOnly(!subscribedOnly)
                                showSchedule = false
                            },
                        ) {
                            Icon(if (subscribedOnly && !showSchedule) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Subscribed only")
                        }
                        IconToggleButton(
                            checked = showSchedule,
                            onCheckedChange = {
                                showSchedule = !showSchedule
                                if (showSchedule) viewModel.loadSchedule()
                            },
                        ) { Icon(Icons.Default.DateRange, contentDescription = "Schedule") }
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close updates") }
                },
            )
            PullToRefreshBox(isRefreshing = state is Load.Loading, onRefresh = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                viewModel.refresh()
            }, modifier = Modifier.fillMaxSize()) {
                if (showSchedule) {
                    LoadView(schedule ?: Load.Loading, onRetry = viewModel::loadSchedule) { schedules ->
                        ScheduleList(schedules, onOpenSeries)
                    }
                } else {
                    LoadView(state, onRetry = viewModel::refresh) { entries ->
                        val rows = remember(entries) { withDayHeadings(entries) }
                        val listState = rememberLazyListState()

                        // Load the next page once the last few rows are on screen. The subscriptions list is complete as loaded.
                        if (!subscribedOnly) {
                            LaunchedEffect(listState, rows.size) {
                                snapshotFlow {
                                    val info = listState.layoutInfo
                                    (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 4
                                }.collect { nearEnd -> if (nearEnd) viewModel.loadMore() }
                            }
                        }

                        Box(Modifier.fillMaxSize()) {
                            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                                if (!subscribedOnly) {
                                    offlineSavedAt?.let { savedAt ->
                                        item { OfflineBanner(savedAt, "updates", onRetry = viewModel::load) }
                                    }
                                }
                                if (entries.isEmpty()) {
                                    item {
                                        Text(
                                            if (subscribedOnly) "None of your subscriptions has a chapter in your language yet." else "No updates right now.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(16.dp),
                                        )
                                    }
                                }
                                items(
                                    rows,
                                    key = { row -> if (row is UpdateRow.Entry) row.entry.series.id else "day-${(row as UpdateRow.Heading).label}" },
                                    contentType = { row -> if (row is UpdateRow.Entry) 0 else 1 },
                                ) { row ->
                                    when (row) {
                                        is UpdateRow.Heading -> Text(
                                            row.label,
                                            style = MaterialTheme.typography.titleSmallEmphasized,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
                                        )
                                        is UpdateRow.Entry -> {
                                            val entry = row.entry
                                            val subscribed = entry.series.id in subscribedIds
                                            UpdateRowItem(
                                                entry = entry,
                                                subscribed = subscribed,
                                                unread = isUnreadUpdate(entry, subscribed, lastRead[entry.series.id]),
                                                onOpenSeries = { onOpenSeries(entry.series.id) },
                                                onOpenChapter = if (entry.chapterId.isNotEmpty()) ({ onOpenChapter(entry.series.id, entry.chapterId) }) else null,
                                                onMarkRead = if (entry.chapterId.isNotEmpty()) ({ viewModel.markRead(entry) }) else null,
                                            )
                                        }
                                    }
                                }
                                if (loadingMore && !subscribedOnly) {
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

        toast?.let { message ->
            LaunchedEffect(message) {
                delay(3.seconds)
                viewModel.clearToast()
            }
            Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Text(message) }
        }
    }
}

/** One update: cover, genre, title, chapter, group, and when. A long press opens the chapter or marks it read. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UpdateRowItem(
    entry: UpdateEntry,
    subscribed: Boolean,
    unread: Boolean,
    onOpenSeries: () -> Unit,
    onOpenChapter: (() -> Unit)?,
    onMarkRead: (() -> Unit)?,
) {
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(
                    onClick = onOpenSeries,
                    onLongClick = if (onOpenChapter != null) (
                        {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menu = true
                        }
                    ) else null,
                    onLongClickLabel = "More",
                ),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(
                    entry.series.coverUrl,
                    entry.series.title,
                    Modifier.width(52.dp).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop,
                    thumb = true,
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    GenreLabel(entry.series.genre)
                    Text(entry.series.title, style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            append("Ep. ${entry.chapterNumber}")
                            if (entry.chapterTitle.isNotBlank()) append(" · ${entry.chapterTitle}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    entry.group?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (unread) {
                            Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        }
                        if (subscribed) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = if (unread) "Subscribed, unread" else stringResource(R.string.subscribed),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    val ago = remember(entry.publishedAt) { timeAgo(entry.publishedAt) }
                    Text(ago, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            onOpenChapter?.let { DropdownMenuItem(text = { Text("Read Ep. ${entry.chapterNumber}") }, onClick = { menu = false; it() }) }
            DropdownMenuItem(text = { Text("Open series page") }, onClick = { menu = false; onOpenSeries() })
            onMarkRead?.let { DropdownMenuItem(text = { Text("Mark read up to here") }, onClick = { menu = false; it() }) }
        }
    }
}
