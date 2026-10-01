package com.dexter.ui.library

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dexter.data.LibraryList
import com.dexter.data.ReadingStatus
import com.dexter.data.SavedSeries
import com.dexter.data.SeriesSummary
import com.dexter.ui.Cover
import com.dexter.ui.Load
import com.dexter.ui.TextPromptDialog
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The three My Series lists as one connected row of toggle buttons. */
@Composable
internal fun LibraryTabs(selected: LibraryList, onSelect: (LibraryList) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        val options = listOf("Recent" to LibraryList.Recent, "Subscribed" to LibraryList.Subscribed, "Lists" to LibraryList.Lists)
        options.forEachIndexed { index, (label, list) ->
            ToggleButton(
                checked = selected == list,
                onCheckedChange = { onSelect(list) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                contentPadding = PaddingValues(horizontal = 8.dp),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** What an empty list says, and the one action that helps: clear the filters, or go find something to read. */
@Composable
internal fun LibraryEmpty(tab: LibraryList, filtering: Boolean, onClearFilters: () -> Unit, onOpenSearch: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when {
                    filtering -> "Nothing matches your filters."
                    tab == LibraryList.Subscribed -> "Subscribe to a series to see it here."
                    tab == LibraryList.Lists -> "Add a series to a list from its page."
                    else -> "Series you read show up here."
                },
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            if (filtering) {
                FilledTonalButton(onClick = onClearFilters, modifier = Modifier.padding(top = 16.dp)) { Text("Clear filters") }
            } else {
                FilledTonalButton(onClick = onOpenSearch, modifier = Modifier.padding(top = 16.dp)) { Text("Find something to read") }
            }
        }
    }
}

/** The bar shown while selecting: how many, and what to do with them. */
@Composable
internal fun SelectionBar(
    count: Int,
    collections: List<String>,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onStatus: (ReadingStatus?) -> Unit,
    onCollection: (String) -> Unit,
    onDownload: () -> Unit,
) {
    var statusMenu by remember { mutableStateOf(false) }
    var collectionMenu by remember { mutableStateOf(false) }
    var newCollection by remember { mutableStateOf(false) }
    if (newCollection) {
        TextPromptDialog(title = "New collection", initial = "", confirmLabel = "Add", onConfirm = onCollection, onDismiss = { newCollection = false })
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$count selected", style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp))
        TextButton(onClick = onSelectAll) { Text("All") }
        Box {
            TextButton(onClick = { statusMenu = true }) { Text("List") }
            DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                ReadingStatus.entries.forEach { status ->
                    DropdownMenuItem(text = { Text(status.label) }, onClick = { statusMenu = false; onStatus(status) })
                }
                DropdownMenuItem(text = { Text("Remove from lists") }, onClick = { statusMenu = false; onStatus(null) })
            }
        }
        Box {
            TextButton(onClick = { collectionMenu = true }) { Text("Collection") }
            DropdownMenu(expanded = collectionMenu, onDismissRequest = { collectionMenu = false }) {
                collections.forEach { name ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { collectionMenu = false; onCollection(name) })
                }
                DropdownMenuItem(text = { Text("New collection…") }, onClick = { collectionMenu = false; newCollection = true })
            }
        }
        TextButton(onClick = onDownload) { Text("Save unread") }
        TextButton(onClick = onDelete) { Text("Delete") }
        TextButton(onClick = onClear) { Text("Done") }
    }
}

/** One series as a cover tile for the grid view, with a badge for new chapters and a tick while selected. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibraryTile(
    series: SavedSeries,
    newLabel: String?,
    selected: Boolean,
    selecting: Boolean,
    onOpen: () -> Unit,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.clip(MaterialTheme.shapes.medium).combinedClickable(
            onClick = { if (selecting) onSelect(!selected) else onOpen() },
            onLongClick = { onSelect(!selected) },
            onLongClickLabel = "Select",
        ),
    ) {
        Column {
            Box {
                Cover(series.coverUrl, null, Modifier.fillMaxWidth().aspectRatio(2f / 3f), contentScale = ContentScale.Crop, thumb = true, sharedKey = series.id)
                newLabel?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (selecting) {
                    Checkbox(checked = selected, onCheckedChange = onSelect, modifier = Modifier.align(Alignment.TopEnd))
                }
            }
            Column(Modifier.padding(8.dp)) {
                Text(series.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                series.chapterNumber?.let { Text("Ep. $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/**
 * A row that can be swiped sideways. Past about a third of the width, a swipe to the right runs [onStart]
 * and one to the left runs [onEnd]. A null label turns that direction off.
 */
@Composable
internal fun SwipeRow(
    startLabel: String?,
    endLabel: String?,
    enabled: Boolean,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var width by remember { mutableIntStateOf(1) }
    Box(
        modifier
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            // TalkBack cannot swipe a row, so the same two actions are offered in its actions menu.
            .semantics {
                if (enabled) {
                    customActions = listOfNotNull(
                        startLabel?.let { label -> CustomAccessibilityAction(label) { onStart(); true } },
                        endLabel?.let { label -> CustomAccessibilityAction(label) { onEnd(); true } },
                    )
                }
            },
    ) {
        val dx = offset.value
        if (dx != 0f) {
            val toStart = dx > 0f
            Row(
                Modifier.matchParentSize().padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(if (toStart) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toStart) Arrangement.Start else Arrangement.End,
            ) {
                Text(
                    (if (toStart) startLabel else endLabel).orEmpty(),
                    color = if (toStart) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Box(
            Modifier
                .offset { IntOffset(dx.roundToInt(), 0) }
                .pointerInput(enabled, startLabel, endLabel) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val threshold = width * SWIPE_THRESHOLD
                                when {
                                    offset.value > threshold && startLabel != null -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                        onStart()
                                        offset.animateTo(0f)
                                    }
                                    offset.value < -threshold && endLabel != null -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                        onEnd()
                                        offset.snapTo(0f)
                                    }
                                    else -> offset.animateTo(0f)
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } },
                    ) { change, amount ->
                        val low = if (endLabel != null) -width.toFloat() else 0f
                        val high = if (startLabel != null) width.toFloat() else 0f
                        scope.launch { offset.snapTo((offset.value + amount).coerceIn(low, high)) }
                        change.consume()
                    }
                },
        ) { content() }
    }
}

private const val SWIPE_THRESHOLD = 0.35f

/** The series hidden from browse and search, each with a button to show it again. */
@Composable
internal fun HiddenSeriesDialog(state: Load<List<SeriesSummary>>, onOpen: (String) -> Unit, onUnhide: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hidden series") },
        text = {
            when (state) {
                Load.Loading -> Text("Loading…")
                is Load.Error -> Text(state.message)
                is Load.Ready -> if (state.value.isEmpty()) {
                    Text("Nothing is hidden.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(state.value, key = { it.id }) { series ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    series.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).clickable { onDismiss(); onOpen(series.id) },
                                )
                                TextButton(onClick = { onUnhide(series.id) }) { Text("Unhide") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
