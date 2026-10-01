package com.dexter.ui.series

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import com.dexter.R
import com.dexter.data.Chapter
import com.dexter.data.ChapterListItem
import com.dexter.data.ReadingStatus
import com.dexter.data.SeriesDetail
import com.dexter.data.factsLine
import com.dexter.data.groupByVolume
import com.dexter.data.languageName
import com.dexter.ui.ChoiceChip
import com.dexter.ui.Cover
import com.dexter.ui.GenreLabel
import com.dexter.ui.Load
import com.dexter.ui.LoadView
import com.dexter.ui.OfflineBanner
import com.dexter.ui.PickTile
import com.dexter.ui.RAIL_MIN_WIDTH_DP
import com.dexter.ui.compact
import com.dexter.ui.formatChapterDate
import com.dexter.ui.iconTap
import com.dexter.ui.timeAgo
import com.dexter.ui.windowWidthDp
import java.util.Locale

@Composable
fun SeriesScreen(
    viewModel: SeriesViewModel,
    onOpenChapter: (chapterId: String) -> Unit,
    onHome: () -> Unit,
    onOpenTag: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenAuthor: (id: String, name: String) -> Unit,
) {
    val similar by viewModel.similar.collectAsState()
    val haptics = LocalHapticFeedback.current
    val related by viewModel.related.collectAsState()
    val covers by viewModel.covers.collectAsState()
    val preferredGroup by viewModel.preferredGroup.collectAsState()
    val state by viewModel.state.collectAsState()
    val notifyEnabled by viewModel.notifyEnabled.collectAsState()
    val lastRead by viewModel.lastRead.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState()
    val offlineSavedAt by viewModel.offlineSavedAt.collectAsState()
    val subscribed by viewModel.subscribed.collectAsState()
    val status by viewModel.status.collectAsState()
    val downloaded by viewModel.downloaded.collectAsState()
    val downloading by viewModel.downloading.collectAsState()
    var downloadMenu by remember { mutableStateOf(false) }
    var statusMenu by remember { mutableStateOf(false) }
    val collections by viewModel.collections.collectAsState()
    var newCollection by remember { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Notifications need permission on Android 13 and later. Ask when the user first subscribes.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LoadView(state, onRetry = viewModel::load) { page ->
            val summary = page.detail.summary
            val readable = page.chapters.filter { it.externalUrl == null }
            // With older chapters still unloaded, the oldest loaded one is not Episode 1.
            val startAt = if (page.hasMore) readable.firstOrNull() else readable.lastOrNull()
            val listState = rememberLazyListState()

            // Load older chapters once the last few rows are on screen.
            LaunchedEffect(listState, page.chapters.size, page.hasMore) {
                snapshotFlow {
                    val info = listState.layoutInfo
                    (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 4
                }.collect { nearEnd -> if (nearEnd && page.hasMore) viewModel.loadMore() }
            }
            val open: (Chapter) -> Unit = { chapter ->
                val link = chapter.externalUrl
                if (link == null) onOpenChapter(chapter.id)
                else context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri()))
            }

            covers?.let { state ->
                Dialog(onDismissRequest = viewModel::closeCovers) {
                    Column(Modifier.clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(20.dp)) {
                        Text(stringResource(R.string.covers), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        when (state) {
                            is Load.Ready -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(state.value, key = { it.url }) { cover ->
                                    Column(Modifier.width(150.dp)) {
                                        Cover(cover.url, null, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                                        Text(cover.volume?.let { "Volume $it" } ?: "No volume", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                            is Load.Error -> Text(state.message)
                            Load.Loading -> Text(stringResource(R.string.loading_2))
                        }
                    }
                }
            }
            newCollection?.let { name ->
                AlertDialog(
                    onDismissRequest = { newCollection = null },
                    title = { Text(stringResource(R.string.new_collection)) },
                    text = {
                        OutlinedTextField(value = name, onValueChange = { newCollection = it }, singleLine = true, placeholder = { Text(stringResource(R.string.name)) })
                    },
                    confirmButton = {
                        TextButton(
                            enabled = name.isNotBlank(),
                            onClick = {
                                viewModel.toggleCollection(page.detail, name.trim())
                                newCollection = null
                            },
                        ) { Text(stringResource(R.string.add)) }
                    },
                    dismissButton = { TextButton(onClick = { newCollection = null }) { Text(stringResource(R.string.cancel)) } },
                )
            }
            if (showInfo) {
                InfoDialog(
                    page.detail,
                    onOpenLink = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) },
                    onOpenCovers = { showInfo = false; viewModel.openCovers() },
                    onDismiss = { showInfo = false },
                )
            }

            val headerContent: LazyListScope.() -> Unit = {
                offlineSavedAt?.let { savedAt ->
                    item {
                        OfflineBanner(savedAt, "a copy", onRetry = { viewModel.load() })
                    }
                }
                item {
                    Box(Modifier.fillMaxWidth().height(340.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Cover(summary.coverUrl, summary.title, Modifier.fillMaxSize())
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.6f), MaterialTheme.colorScheme.background),
                                ),
                            ),
                        )
                        // Top bar: home, info, share.
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilledTonalIconButton(onClick = onHome) {
                                Icon(Icons.Default.Home, contentDescription = stringResource(R.string.home))
                            }
                            Spacer(Modifier.weight(1f))
                            FilledTonalIconButton(onClick = { showInfo = true }) {
                                Icon(Icons.Default.Info, contentDescription = stringResource(R.string.info))
                            }
                            FilledTonalIconButton(
                                onClick = {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "${summary.title}\nhttps://mangadex.org/title/${summary.id}")
                                    }
                                    context.startActivity(Intent.createChooser(send, null))
                                },
                            ) {
                                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                            }
                        }
                        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp)) {
                            GenreLabel(summary.genre)
                            Text(summary.title, style = MaterialTheme.typography.headlineLargeEmphasized, color = MaterialTheme.colorScheme.onBackground)
                            val authorId = summary.authorId
                            Text(
                                summary.author.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = if (authorId != null && !summary.author.isNullOrBlank()) {
                                    Modifier.clickable { onOpenAuthor(authorId, summary.author.orEmpty()) }
                                } else {
                                    Modifier
                                },
                            )
                            Text(factsLine(page.detail), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                summary.follows?.let {
                                    Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                                    Text(" ${compact(it)}   ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                page.detail.rating?.let {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                                    Text(" %.2f".format(Locale.US, it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                item {
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ToggleButton(
                            checked = subscribed,
                            onCheckedChange = {
                                haptics.performHapticFeedback(if (subscribed) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                                if (!subscribed) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                viewModel.toggleSubscribed(page.detail)
                            },
                        ) { Text(if (subscribed) "Subscribed" else "Subscribe") }
                        if (subscribed) {
                            FilledTonalIconToggleButton(checked = notifyEnabled, onCheckedChange = { viewModel.setNotify(it) }) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = if (notifyEnabled) "Notifications on for this series" else "Notifications off for this series",
                                )
                            }
                        }
                        Box {
                            OutlinedButton(onClick = { statusMenu = true }) { Text(status?.label ?: "Add to list") }
                            DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                                ReadingStatus.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            viewModel.setStatus(page.detail, option)
                                            statusMenu = false
                                        },
                                    )
                                }
                                collections.forEach { (name, inIt) ->
                                    DropdownMenuItem(
                                        text = { Text(if (inIt) "✓ $name" else name) },
                                        onClick = {
                                            viewModel.toggleCollection(page.detail, name)
                                            statusMenu = false
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("New collection…") },
                                    onClick = {
                                        statusMenu = false
                                        newCollection = ""
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Hide from lists and search") },
                                    onClick = {
                                        viewModel.hideSeries()
                                        statusMenu = false
                                    },
                                )
                                if (status != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.remove_from_lists)) },
                                        onClick = {
                                            viewModel.setStatus(page.detail, null)
                                            statusMenu = false
                                        },
                                    )
                                }
                            }
                        }
                        Box {
                            OutlinedButton(onClick = { downloadMenu = true }) { Text("Save") }
                            DropdownMenu(expanded = downloadMenu, onDismissRequest = { downloadMenu = false }) {
                                listOf("Next 5 unread" to 5, "Next 10 unread" to 10, "All unread" to null).forEach { (label, count) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            viewModel.downloadUnread(page.detail, count)
                                            downloadMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                if (summary.description.isNotBlank()) {
                    item { Description(summary.description) }
                }
                if (page.detail.tags.isNotEmpty()) {
                    item { TagChips(page.detail.tags, onOpenTag) }
                }
                if (related.isNotEmpty()) {
                    item {
                        Text(stringResource(R.string.related), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp))
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(related, key = { it.second.id }) { (kind, other) ->
                                Column(Modifier.width(110.dp)) {
                                    Text(kind, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
                                    PickTile(other, { onOpenSeries(other.id) }, Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
                if (similar.isNotEmpty()) {
                    item {
                        Text(stringResource(R.string.similar_series), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp))
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(similar, key = { it.id }) { other ->
                                PickTile(other, { onOpenSeries(other.id) }, Modifier.width(110.dp))
                            }
                        }
                    }
                }
                if (page.chapters.isEmpty() && !page.hasMore) {
                    item {
                        Text(
                            "No chapters in ${languageName(viewModel.language)} yet. Change the language in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            val chapterContent: LazyListScope.() -> Unit = {
                item {
                    if (lastRead == null && startAt == null && page.chapters.isNotEmpty()) {
                        Text(
                            "This series is hosted by its publisher. Episodes open in your browser.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    val resumeId = lastRead?.chapterId
                    if (resumeId != null || startAt != null) {
                        Button(
                            onClick = { if (resumeId != null) onOpenChapter(resumeId) else open(startAt!!) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = ButtonDefaults.MediumContainerHeight),
                        ) {
                            Text(
                                when {
                                    resumeId != null -> {
                                        val unread = unreadChapterCount(page.chapters, lastRead?.chapterNumber)
                                        "Continue Ep. ${lastRead?.chapterNumber}" + if (unread > 0) " \u00b7 $unread new" else ""
                                    }
                                    page.hasMore -> "Latest Ep. ${startAt!!.number}"
                                    else -> "Episode ${startAt!!.number}"
                                },
                                style = MaterialTheme.typography.titleMediumEmphasized,
                            )
                        }
                    }
                }
                val listItems = groupByVolume(page.chapters)
                items(listItems, key = { item -> if (item is ChapterListItem.Entry) item.chapter.id else "volume-${(item as ChapterListItem.VolumeHeader).label}" }) { item ->
                    when (item) {
                        is ChapterListItem.VolumeHeader -> Text(
                            item.label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                        )
                        is ChapterListItem.Entry -> {
                            val chapter = item.chapter
                            val readable = chapter.externalUrl == null
                            val previous = previousReadable(page.chapters, chapter)
                            EpisodeRow(
                                chapter,
                                summary.coverUrl,
                                read = isChapterRead(chapter.number, lastRead?.chapterNumber),
                                preferredGroup = preferredGroup,
                                saved = chapter.id in downloaded,
                                saving = chapter.id in downloading,
                                onDownload = { viewModel.download(page.detail, chapter) },
                                onRemoveDownload = { viewModel.removeDownload(chapter.id) },
                                onClick = { open(chapter) },
                                // Read marks apply to chapters that open in the reader.
                                onMarkRead = if (readable) ({ viewModel.markReadUpTo(chapter, page.detail) }) else null,
                                // Marking unread needs an earlier chapter to fall back to, or the full list.
                                onMarkUnread = if (readable && (previous != null || !page.hasMore)) ({ viewModel.markUnreadFrom(previous, page.detail) }) else null,
                                onPreferGroup = viewModel::setPreferredGroup,
                                onBlockGroup = viewModel::blockGroup,
                                onOpenUpload = { upload -> open(upload) },
                            )
                        }
                    }
                }
                if (loadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator(Modifier.size(40.dp))
                        }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
            // On a wide screen the description sits beside the chapter list, so both scroll on their own.
            if (windowWidthDp() >= RAIL_MIN_WIDTH_DP) {
                Row(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.weight(0.42f).fillMaxSize(), content = headerContent)
                    LazyColumn(Modifier.weight(0.58f).fillMaxSize(), state = listState, content = chapterContent)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    headerContent()
                    chapterContent()
                }
            }
        }
    }
}

/** Shows three lines. Tapping toggles the full text, with a hint only when text is cut off. */
@Composable
private fun Description(text: String) {
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
private fun EpisodeRow(
    chapter: Chapter,
    coverUrl: String?,
    read: Boolean,
    onClick: () -> Unit,
    onMarkRead: (() -> Unit)?,
    onMarkUnread: (() -> Unit)?,
    preferredGroup: String?,
    saved: Boolean,
    saving: Boolean,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onPreferGroup: (String?) -> Unit,
    onBlockGroup: (String) -> Unit,
    onOpenUpload: (Chapter) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (read) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 3.dp)
                .alpha(if (read) 0.55f else 1f)
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(onClick = onClick, onLongClick = if (onMarkRead != null || chapter.alternates.isNotEmpty() || chapter.externalUrl == null) ({ menu = true }) else null),
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
                    Text(
                        listOfNotNull(formatChapterDate(chapter.publishedAt), chapter.group, if (saved) "Saved" else if (saving) "Saving..." else null).joinToString(" · "),
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
                } else if (!saving) {
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

/** The series' tags. Tapping one searches it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagChips(tags: List<String>, onOpenTag: (String) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag -> ChoiceChip(tag, selected = false) { onOpenTag(tag) } }
    }
}

@Composable
private fun InfoDialog(detail: SeriesDetail, onOpenLink: (String) -> Unit, onOpenCovers: () -> Unit, onDismiss: () -> Unit) {
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
            if (detail.links.isNotEmpty()) {
                Text(stringResource(R.string.links), style = MaterialTheme.typography.labelLargeEmphasized, modifier = Modifier.padding(top = 12.dp))
                detail.links.forEach { link ->
                    TextButton(onClick = { onOpenLink(link.url) }) { Text(link.label) }
                }
            }
        }
    }
}
