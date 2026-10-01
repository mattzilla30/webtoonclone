package com.dexter.ui.series

import android.content.ClipData
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.dexter.R
import com.dexter.data.Chapter
import com.dexter.data.ReadingStatus
import com.dexter.data.SeriesDetail
import com.dexter.data.languageName
import com.dexter.ui.ChoiceChip
import com.dexter.ui.Cover
import com.dexter.ui.formatChapterDate
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
    coverUrl: String?,
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
) {
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (read) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 3.dp)
                .alpha(if (read) 0.55f else 1f)
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (onMarkRead != null || chapter.alternates.isNotEmpty() || chapter.externalUrl == null) (
                        {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menu = true
                        }
                    ) else null,
                ),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(coverUrl, null, Modifier.width(40.dp).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.extraSmall), contentScale = ContentScale.Crop, thumb = true)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
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

/** The series' tags. Tapping one searches it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagChips(tags: List<String>, onOpenTag: (String) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag -> ChoiceChip(tag, selected = false) { onOpenTag(tag) } }
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
            Text(detail.status.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLargeEmphasized)
            val facts = listOfNotNull(
                detail.year?.toString(),
                detail.demographic,
                languageName(detail.originalLanguage).takeIf { it.isNotEmpty() },
            )
            if (facts.isNotEmpty()) {
                Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            Text(detail.summary.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
            if (!detail.summary.author.isNullOrBlank()) {
                Text(stringResource(R.string.written_by), style = MaterialTheme.typography.labelLargeEmphasized)
                Text(detail.summary.author, style = MaterialTheme.typography.bodyMedium)
            }
            if (detail.altTitles.isNotEmpty()) {
                Text(stringResource(R.string.also_known_as), style = MaterialTheme.typography.labelLargeEmphasized, modifier = Modifier.padding(top = 12.dp))
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
