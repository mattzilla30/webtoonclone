package com.dexter.ui.series

import android.content.ClipData
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.dexter.R
import com.dexter.data.Bookmark
import com.dexter.data.Chapter
import com.dexter.data.ReadingStatus
import com.dexter.data.SeriesDetail
import com.dexter.data.TropeTag
import com.dexter.ui.ChoiceChip
import com.dexter.ui.Cover
import com.dexter.ui.formatChapterDate
import com.dexter.ui.reader.ZoomState
import com.dexter.ui.reader.zoomGestures
import kotlinx.coroutines.launch

/** Shows three lines. Tapping toggles the full text, with a hint only when text is cut off. */
@Composable
internal fun Description(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var cutOff by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth()
            .clickable(enabled = cutOff || expanded) { expanded = !expanded }
            .animateContentSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
            onTextLayout = { if (!expanded) cutOff = it.hasVisualOverflow },
        )
        if (cutOff || expanded) {
            Text(
                if (expanded) "Show less" else "Read more",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EpisodeRow(
    chapter: Chapter,
    read: Boolean,
    onClick: () -> Unit,
    onMarkRead: (() -> Unit)?,
    onMarkUnread: (() -> Unit)?,
    preferredGroup: String?,
    saved: Boolean,
    /** Progress from 0 to 1 while waiting or saving, or null when the chapter is not in the queue. */
    saving: Float?,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onPreferGroup: (String?) -> Unit,
    onBlockGroup: (String) -> Unit,
    onOpenUpload: (Chapter) -> Unit,
    selecting: Boolean = false,
    picked: Boolean = false,
    onToggle: () -> Unit = {},
    onRangeTo: () -> Unit = {},
    onStartSelecting: () -> Unit = {},
    onComments: () -> Unit = {},
    /** Hides the chapter from the list, downloads, and update checks, until unblacklisted in settings. */
    onBlacklist: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        Surface(
            shape = MaterialTheme.shapes.medium,
            // Flat rows; read episodes are dimmed below, and a picked one keeps its highlight.
            color = if (picked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 3.dp)
                .alpha(if (read) 0.55f else 1f)
                .clip(MaterialTheme.shapes.medium)
                // While selecting, a tap picks the chapter and a long press picks every chapter up to it.
                .combinedClickable(
                    onClick = if (selecting) onToggle else onClick,
                    onLongClick = when {
                        selecting -> (
                            {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRangeTo()
                            }
                        )
                        else -> (
                            {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                menu = true
                            }
                        )
                    },
                    onLongClickLabel = if (selecting) "Select up to here" else "More",
                ),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // No cover per row: every episode would repeat the same series cover.
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(
                        buildString {
                            append("Ep. ${chapter.number}")
                            if (chapter.title.isNotBlank()) append(" · ${chapter.title}")
                            if (chapter.externalUrl != null) append("  ↗")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    // Parsing the date once per chapter, not once per draw of the row.
                    val date = remember(chapter.publishedAt) { formatChapterDate(chapter.publishedAt) }
                    Text(
                        listOfNotNull(date, chapter.group, savingLabel(saved, saving)).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (chapter.externalUrl == null) {
                DropdownMenuItem(text = { Text("Select") }, onClick = { menu = false; onStartSelecting() })
            }
            DropdownMenuItem(text = { Text("Comments") }, onClick = { menu = false; onComments() })
            if (onBlacklist != null) {
                DropdownMenuItem(text = { Text("Never show again") }, onClick = { menu = false; onBlacklist() })
            }
            if (onMarkRead != null) {
                DropdownMenuItem(text = { Text(stringResource(R.string.mark_read_up_to_here)) }, onClick = { menu = false; onMarkRead() })
            }
            if (onMarkUnread != null) {
                DropdownMenuItem(text = { Text(stringResource(R.string.mark_unread_from_here)) }, onClick = { menu = false; onMarkUnread() })
            }
            if (chapter.externalUrl == null) {
                if (saved) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.remove_download)) }, onClick = { menu = false; onRemoveDownload() })
                } else if (saving != null) {
                    DropdownMenuItem(text = { Text("Cancel download") }, onClick = { menu = false; onCancelDownload() })
                } else {
                    DropdownMenuItem(text = { Text(stringResource(R.string.download)) }, onClick = { menu = false; onDownload() })
                }
            }
            chapter.group?.let { group ->
                if (group == preferredGroup) {
                    DropdownMenuItem(text = { Text("Stop preferring $group") }, onClick = { menu = false; onPreferGroup(null) })
                } else {
                    DropdownMenuItem(text = { Text("Prefer $group") }, onClick = { menu = false; onPreferGroup(group) })
                }
                DropdownMenuItem(text = { Text("Block $group") }, onClick = { menu = false; onBlockGroup(group) })
            }
            chapter.alternates.forEach { upload ->
                DropdownMenuItem(
                    text = { Text("Read ${upload.group ?: "other upload"}") },
                    onClick = { menu = false; onOpenUpload(upload) },
                )
            }
        }
    }
}

/** "Saved", "Saving 42%", "Queued", or null when the chapter is neither saved nor in the queue. */
fun savingLabel(saved: Boolean, progress: Float?): String? = when {
    saved -> "Saved"
    progress == null -> null
    progress > 0f -> "Saving ${(progress * 100).toInt()}%"
    else -> "Queued"
}

/** The series' tags. Tapping one searches it, and a long press offers to block it. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun TagChips(tags: List<String>, onOpenTag: (String) -> Unit, onBlockTag: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag ->
            var menu by remember { mutableStateOf(false) }
            Box {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.clip(MaterialTheme.shapes.small).combinedClickable(
                        onClick = { onOpenTag(tag) },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menu = true
                        },
                        onLongClickLabel = "Block this tag",
                    ),
                ) {
                    Text(tag, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Search $tag") }, onClick = { menu = false; onOpenTag(tag) })
                    DropdownMenuItem(text = { Text("Block $tag") }, onClick = { menu = false; onBlockTag(tag) })
                }
            }
        }
    }
}

/** Your note on the series. Tapping it edits it. */
@Composable
internal fun NoteCard(note: String, onEdit: () -> Unit) {
    Surface(
        onClick = onEdit,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Your note", style = MaterialTheme.typography.labelLargeEmphasized)
            Text(note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Filters and order for the chapter list, going to a chapter, and picking chapters to save or mark read. */
@Composable
internal fun ChapterControls(
    unreadOnly: Boolean,
    savedOnly: Boolean,
    oldestFirst: Boolean,
    selecting: Boolean,
    pickedCount: Int,
    onUnreadOnly: (Boolean) -> Unit,
    onSavedOnly: (Boolean) -> Unit,
    onOldestFirst: (Boolean) -> Unit,
    onJump: () -> Unit,
    onSelecting: (Boolean) -> Unit,
    onPickAll: () -> Unit,
    onSavePicked: () -> Unit,
    onMarkPickedRead: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChoiceChip("Unread", unreadOnly) { onUnreadOnly(!unreadOnly) }
            ChoiceChip("Saved", savedOnly) { onSavedOnly(!savedOnly) }
            ChoiceChip(if (oldestFirst) "Oldest first" else "Newest first", oldestFirst) { onOldestFirst(!oldestFirst) }
            TextButton(onClick = onJump) { Text("Go to\u2026") }
            TextButton(onClick = { onSelecting(!selecting) }) { Text(if (selecting) "Done" else "Select") }
        }
        if (selecting) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (pickedCount == 0) "Tap chapters, or press and hold to pick a range" else "$pickedCount picked",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                TextButton(onClick = onPickAll) { Text("All") }
                TextButton(enabled = pickedCount > 0, onClick = onSavePicked) { Text("Save") }
                TextButton(enabled = pickedCount > 0, onClick = onMarkPickedRead) { Text("Mark read") }
            }
        }
    }
}

/** The cover full screen, with pinch and double-tap zoom, and a button to save it. */
@Composable
internal fun CoverViewer(url: String?, title: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    if (url == null) return
    val zoom = remember { ZoomState() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .onSizeChanged { size = it }
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { zoom.toggle(it, size) }) }
                .zoomGestures(zoom) { size },
        ) {
            AsyncImage(
                model = url,
                contentDescription = "$title cover",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offsetX
                    translationY = zoom.offsetY
                },
            )
            Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onSave(url) }) { Text("Save") }
                FilledTonalButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

@Composable
internal fun InfoDialog(detail: SeriesDetail, onOpenLink: (String) -> Unit, onOpenCovers: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            // Only what the series page does not already show: status, facts, author and description live there.
            if (detail.altTitles.isNotEmpty()) {
                Text(stringResource(R.string.also_known_as), style = MaterialTheme.typography.labelLargeEmphasized)
                detail.altTitles.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp)) }
            }
            if (detail.ratingDistribution.isNotEmpty()) {
                Text(stringResource(R.string.ratings), style = MaterialTheme.typography.labelLargeEmphasized, modifier = Modifier.padding(top = 12.dp))
                val most = detail.ratingDistribution.values.max().coerceAtLeast(1)
                detail.ratingDistribution.forEach { (score, count) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Text("$score", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(20.dp))
                        Box(Modifier.height(8.dp).fillMaxWidth(count.toFloat() / most * 0.6f).background(MaterialTheme.colorScheme.primary))
                        Text(" $count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            TextButton(onClick = onOpenCovers) { Text(stringResource(R.string.covers)) }
            TextButton(onClick = { onOpenLink("https://mangadex.org/title/${detail.summary.id}") }) { Text("Open on MangaDex") }
            val clipboard = LocalClipboard.current
            val scope = rememberCoroutineScope()
            TextButton(
                onClick = {
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Title", detail.summary.title))) }
                },
            ) { Text("Copy title") }
            TextButton(
                onClick = {
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Link", "https://mangadex.org/title/${detail.summary.id}"))) }
                },
            ) { Text("Copy link") }
            if (detail.links.isNotEmpty()) {
                Text(stringResource(R.string.links), style = MaterialTheme.typography.labelLargeEmphasized, modifier = Modifier.padding(top = 12.dp))
                detail.links.forEach { link ->
                    TextButton(onClick = { onOpenLink(link.url) }) { Text(link.label) }
                }
            }
        }
    }
}

/** The "Add to list" menu: a reading status, your own collections, and hiding the series. */
@Composable
internal fun ListMenu(
    expanded: Boolean,
    status: ReadingStatus?,
    collections: Map<String, Boolean>,
    onDismiss: () -> Unit,
    onSetStatus: (ReadingStatus?) -> Unit,
    onToggleCollection: (String) -> Unit,
    onNewCollection: () -> Unit,
    onHide: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        ReadingStatus.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label) },
                onClick = {
                    onSetStatus(option)
                    onDismiss()
                },
            )
        }
        collections.forEach { (name, inIt) ->
            DropdownMenuItem(
                text = { Text(if (inIt) "✓ $name" else name) },
                onClick = {
                    onToggleCollection(name)
                    onDismiss()
                },
            )
        }
        DropdownMenuItem(
            text = { Text("New collection…") },
            onClick = {
                onDismiss()
                onNewCollection()
            },
        )
        DropdownMenuItem(
            text = { Text("Hide from lists and search") },
            onClick = {
                onHide()
                onDismiss()
            },
        )
        if (status != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remove_from_lists)) },
                onClick = {
                    onSetStatus(null)
                    onDismiss()
                },
            )
        }
    }
}

/** The "Save" menu: how many unread chapters to keep on the device. A null count means all of them. */
@Composable
internal fun DownloadMenu(expanded: Boolean, onDismiss: () -> Unit, onPick: (count: Int?) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        listOf("Next 5 unread" to 5, "Next 10 unread" to 10, "All unread" to null).forEach { (label, count) ->
            DropdownMenuItem(
                text = { Text(label) },
                onClick = {
                    onPick(count)
                    onDismiss()
                },
            )
        }
    }
}

/** Your bookmarked pages in this series. Tapping one opens the reader at that page. */
@Composable
internal fun BookmarksCard(bookmarks: List<Bookmark>, onOpen: (Bookmark) -> Unit, onRemove: (Bookmark) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text("Bookmarks", style = MaterialTheme.typography.labelLargeEmphasized, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            bookmarks.forEach { bookmark ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(bookmark) }.padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Ep. ${bookmark.chapterNumber}, page ${bookmark.page + 1}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemove(bookmark) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove bookmark")
                    }
                }
            }
        }
    }
}

/** Your MangaDex rating from 1 to 10, or none. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RatingDialog(current: Int?, onRate: (Int?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your rating") },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..10).forEach { value ->
                    ChoiceChip(value.toString(), current == value) {
                        onRate(value)
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            if (current != null) {
                TextButton(onClick = {
                    onRate(null)
                    onDismiss()
                }) { Text("Remove rating") }
            }
        },
    )
}
