package com.dexter.ui.series

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexter.R
import com.dexter.data.Chapter
import com.dexter.data.ChapterListItem
import com.dexter.data.factsLine
import com.dexter.data.groupByVolume
import com.dexter.data.languageName
import com.dexter.data.nextChapterEstimate
import com.dexter.ui.Cover
import com.dexter.ui.GenreLabel
import com.dexter.ui.Load
import com.dexter.ui.LoadView
import com.dexter.ui.OfflineBanner
import com.dexter.ui.PickTile
import com.dexter.ui.RAIL_MIN_WIDTH_DP
import com.dexter.ui.TextPromptDialog
import com.dexter.ui.compact
import com.dexter.ui.windowWidthDp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Composable
fun SeriesScreen(
    viewModel: SeriesViewModel,
    onOpenChapter: (chapterId: String) -> Unit,
    onOpenBookmark: (chapterId: String, page: Int) -> Unit,
    onHome: () -> Unit,
    onOpenTag: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenAuthor: (id: String, name: String) -> Unit,
) {
    val similar by viewModel.similar.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val related by viewModel.related.collectAsStateWithLifecycle()
    val covers by viewModel.covers.collectAsStateWithLifecycle()
    val preferredGroup by viewModel.preferredGroup.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notifyEnabled by viewModel.notifyEnabled.collectAsStateWithLifecycle()
    val lastRead by viewModel.lastRead.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val offlineSavedAt by viewModel.offlineSavedAt.collectAsStateWithLifecycle()
    val subscribed by viewModel.subscribed.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val downloaded by viewModel.downloaded.collectAsStateWithLifecycle()
    val downloading by viewModel.downloading.collectAsStateWithLifecycle()
    var downloadMenu by remember { mutableStateOf(false) }
    var statusMenu by remember { mutableStateOf(false) }
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    var newCollection by remember { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val note by viewModel.note.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    var oldestFirst by rememberSaveable { mutableStateOf(false) }
    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    var savedOnly by rememberSaveable { mutableStateOf(false) }
    var askJump by remember { mutableStateOf(false) }
    var pendingJump by remember { mutableStateOf<String?>(null) }
    var selecting by remember { mutableStateOf(false) }
    val picked = remember { mutableStateSetOf<String>() }
    var anchor by remember { mutableStateOf<String?>(null) }
    var editNote by remember { mutableStateOf(false) }
    var rate by remember { mutableStateOf(false) }
    val rating by viewModel.rating.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    var coverOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Notifications need permission on Android 13 and later. Ask when the user first subscribes.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LoadView(state, onRetry = viewModel::load) { page ->
            val summary = page.detail.summary
            val readable = remember(page.chapters) { page.chapters.filter { it.externalUrl == null } }
            // The chapters as shown: filtered to unread or saved ones, and oldest first when chosen.
            val shown = remember(page.chapters, oldestFirst, unreadOnly, savedOnly, lastRead?.chapterNumber, downloaded) {
                var list = page.chapters
                if (unreadOnly) list = list.filter { it.externalUrl == null && !isChapterRead(it.number, lastRead?.chapterNumber) }
                if (savedOnly) list = list.filter { it.id in downloaded }
                if (oldestFirst) list.asReversed() else list
            }
            val listItems = remember(shown) { groupByVolume(shown) }
            val nextExpected = remember(page.chapters, page.detail.status) { nextChapterEstimate(page.chapters.map { it.publishedAt }, page.detail.status) }
            // Oldest first and the saved filter need the whole list, not only the newest pages.
            LaunchedEffect(oldestFirst, savedOnly, page.hasMore) { if ((oldestFirst || savedOnly) && page.hasMore) viewModel.loadAll() }
            val previousOf = remember(page.chapters) { previousReadableMap(page.chapters) }
            val unreadCount = remember(page.chapters, lastRead?.chapterNumber) { unreadChapterCount(page.chapters, lastRead?.chapterNumber) }
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
                        Text(stringResource(R.string.covers), style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(bottom = 8.dp))
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
            if (rate) {
                RatingDialog(rating, onRate = viewModel::setRating, onDismiss = { rate = false })
            }
            if (editNote) {
                TextPromptDialog(
                    title = "Your note",
                    initial = note,
                    confirmLabel = "Save",
                    onConfirm = viewModel::setNote,
                    onDismiss = { editNote = false },
                    singleLine = false,
                )
            }
            if (askJump) {
                TextPromptDialog(
                    title = "Go to chapter",
                    initial = "",
                    confirmLabel = "Go",
                    onConfirm = { typed -> pendingJump = typed.trim() },
                    onDismiss = { askJump = false },
                )
            }
            if (coverOpen) {
                CoverViewer(
                    url = summary.coverUrl,
                    title = summary.title,
                    onSave = { url -> viewModel.saveCover(url, summary.title) },
                    onDismiss = { coverOpen = false },
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
                        Cover(
                            summary.coverUrl,
                            summary.title,
                            Modifier.fillMaxSize().clickable(enabled = summary.coverUrl != null, onClickLabel = "Open the cover") { coverOpen = true },
                        )
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
                            nextExpected?.let { day ->
                                Text(
                                    if (day.isAfter(LocalDate.now())) "Next chapter likely around ${day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}" else "Next chapter expected any day",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
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
                            ListMenu(
                                expanded = statusMenu,
                                status = status,
                                collections = collections,
                                onDismiss = { statusMenu = false },
                                onSetStatus = { viewModel.setStatus(page.detail, it) },
                                onToggleCollection = { viewModel.toggleCollection(page.detail, it) },
                                onNewCollection = { newCollection = "" },
                                onHide = viewModel::hideSeries,
                            )
                        }
                        OutlinedButton(onClick = { editNote = true }) { Text(if (note.isBlank()) "Add note" else "Edit note") }
                        if (signedIn) {
                            OutlinedButton(onClick = { rate = true }) { Text(rating?.let { "Rated $it" } ?: "Rate") }
                        }
                        Box {
                            OutlinedButton(onClick = { downloadMenu = true }) { Text("Save") }
                            DownloadMenu(expanded = downloadMenu, onDismiss = { downloadMenu = false }) { count -> viewModel.downloadUnread(page.detail, count) }
                        }
                    }
                }
                if (note.isNotBlank()) {
                    item { NoteCard(note, onEdit = { editNote = true }) }
                }
                if (bookmarks.isNotEmpty()) {
                    item { BookmarksCard(bookmarks, onOpen = { onOpenBookmark(it.chapterId, it.page) }, onRemove = viewModel::removeBookmark) }
                }
                if (summary.description.isNotBlank()) {
                    item { Description(summary.description) }
                }
                if (page.detail.tags.isNotEmpty()) {
                    item { TagChips(page.detail.tags, onOpenTag, onBlockTag = viewModel::blockTag) }
                }
                if (related.isNotEmpty()) {
                    item {
                        Text(stringResource(R.string.related), style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp))
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
                        Text(stringResource(R.string.similar_series), style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp))
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
                                    resumeId != null -> buildString {
                                        append("Continue Ep. ${lastRead?.chapterNumber}")
                                        progress?.takeIf { it.chapterId == resumeId && it.total > 0 }?.let { append(" \u00b7 page ${it.page + 1} of ${it.total}") }
                                        if (unreadCount > 0) append(" \u00b7 $unreadCount new")
                                    }
                                    page.hasMore -> "Latest Ep. ${startAt!!.number}"
                                    else -> "Episode ${startAt!!.number}"
                                },
                                style = MaterialTheme.typography.titleMediumEmphasized,
                            )
                        }
                    }
                }
                item(key = "controls") {
                    ChapterControls(
                        unreadOnly = unreadOnly,
                        savedOnly = savedOnly,
                        oldestFirst = oldestFirst,
                        selecting = selecting,
                        pickedCount = picked.size,
                        onUnreadOnly = { unreadOnly = it },
                        onSavedOnly = { savedOnly = it },
                        onOldestFirst = { oldestFirst = it },
                        onJump = { askJump = true },
                        onSelecting = { on ->
                            selecting = on
                            if (!on) picked.clear()
                        },
                        onPickAll = { picked.addAll(shown.filter { it.externalUrl == null }.map { it.id }) },
                        onSavePicked = {
                            viewModel.downloadMany(page.detail, shown.filter { it.id in picked })
                            selecting = false
                            picked.clear()
                        },
                        onMarkPickedRead = {
                            shown.filter { it.id in picked && it.externalUrl == null }
                                .maxByOrNull { it.number.toDoubleOrNull() ?: Double.MIN_VALUE }
                                ?.let { viewModel.markReadUpTo(it, page.detail) }
                            selecting = false
                            picked.clear()
                        },
                    )
                }
                items(
                    listItems,
                    key = { item -> if (item is ChapterListItem.Entry) item.chapter.id else "volume-${(item as ChapterListItem.VolumeHeader).label}" },
                    // Headings and chapter rows recycle separately, so a scrolled-off row is reused for another row.
                    contentType = { item -> if (item is ChapterListItem.Entry) 0 else 1 },
                ) { item ->
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
                            val previous = previousOf[chapter.id]
                            EpisodeRow(
                                chapter,
                                summary.coverUrl,
                                read = isChapterRead(chapter.number, lastRead?.chapterNumber),
                                preferredGroup = preferredGroup,
                                saved = chapter.id in downloaded,
                                saving = downloading[chapter.id],
                                onDownload = { viewModel.download(page.detail, chapter) },
                                onRemoveDownload = { viewModel.removeDownload(chapter.id) },
                                onCancelDownload = { viewModel.cancelDownload(chapter.id) },
                                onClick = { open(chapter) },
                                // Read marks apply to chapters that open in the reader.
                                onMarkRead = if (readable) ({ viewModel.markReadUpTo(chapter, page.detail) }) else null,
                                // Marking unread needs an earlier chapter to fall back to, or the full list.
                                onMarkUnread = if (readable && (previous != null || !page.hasMore)) ({ viewModel.markUnreadFrom(previous, page.detail) }) else null,
                                onPreferGroup = viewModel::setPreferredGroup,
                                onBlockGroup = viewModel::blockGroup,
                                onOpenUpload = { upload -> open(upload) },
                                selecting = selecting,
                                picked = chapter.id in picked,
                                onToggle = {
                                    if (chapter.id in picked) picked.remove(chapter.id) else picked.add(chapter.id)
                                    anchor = chapter.id
                                },
                                onRangeTo = {
                                    // Picks every chapter between the last one tapped and this one.
                                    val from = shown.indexOfFirst { it.id == anchor }.takeIf { it >= 0 } ?: shown.indexOfFirst { it.id == chapter.id }
                                    val to = shown.indexOfFirst { it.id == chapter.id }
                                    shown.subList(minOf(from, to), maxOf(from, to) + 1).filter { it.externalUrl == null }.forEach { picked.add(it.id) }
                                    anchor = chapter.id
                                },
                                onStartSelecting = {
                                    selecting = true
                                    picked.add(chapter.id)
                                    anchor = chapter.id
                                },
                                onComments = { viewModel.openComments(chapter) { url -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } },
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
            // Go to a typed chapter number. It may sit in a page not loaded yet, so the whole list loads first.
            val wide = windowWidthDp() >= RAIL_MIN_WIDTH_DP
            LaunchedEffect(pendingJump, listItems) {
                val wanted = pendingJump ?: return@LaunchedEffect
                val at = listItems.indexOfFirst { it is ChapterListItem.Entry && (it.chapter.number == wanted || it.chapter.number.toDoubleOrNull() == wanted.toDoubleOrNull()) }
                when {
                    at >= 0 -> {
                        // Items before the chapters: the header rows on a narrow screen, then the resume button and the controls.
                        val before = if (wide) 0 else headerItemCount(offlineSavedAt != null, note.isNotBlank(), bookmarks.isNotEmpty(), summary.description.isNotBlank(), page.detail.tags.isNotEmpty(), related.isNotEmpty(), similar.isNotEmpty(), page.chapters.isEmpty() && !page.hasMore)
                        listState.animateScrollToItem(before + 2 + at)
                        pendingJump = null
                    }
                    page.hasMore -> viewModel.loadAll()
                    else -> pendingJump = null
                }
            }
            // On a wide screen the description sits beside the chapter list, so both scroll on their own.
            if (wide) {
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
        toast?.let { message ->
            LaunchedEffect(message) {
                delay(3.seconds)
                viewModel.clearToast()
            }
            Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Text(message) }
        }
    }
}

/** How many list items come before the chapters on a narrow screen. It must match the header built above. */
private fun headerItemCount(offline: Boolean, note: Boolean, bookmarks: Boolean, description: Boolean, tags: Boolean, related: Boolean, similar: Boolean, noChapters: Boolean): Int =
    listOf(offline, true, true, note, bookmarks, description, tags, related, similar, noChapters).count { it }
