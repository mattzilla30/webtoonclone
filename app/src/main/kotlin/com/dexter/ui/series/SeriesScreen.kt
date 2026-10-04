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
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.OutlinedIconButton
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexter.R
import com.dexter.data.Chapter
import com.dexter.data.ChapterBlacklist
import com.dexter.data.ChapterListItem
import com.dexter.data.ReadingListStore
import com.dexter.data.WANT_TO_READ_LIST_ID
import com.dexter.data.factsLine
import com.dexter.data.groupByVolume
import com.dexter.data.languageName
import com.dexter.data.nextChapterEstimate
import com.dexter.data.withoutBlacklisted
import com.dexter.ui.Cover
import com.dexter.ui.FitText
import com.dexter.ui.Load
import com.dexter.ui.LoadView
import com.dexter.ui.OfflineBanner
import com.dexter.ui.PickTile
import com.dexter.ui.RAIL_MIN_WIDTH_DP
import com.dexter.ui.TextPromptDialog
import com.dexter.ui.compact
import com.dexter.ui.openLink
import com.dexter.ui.windowWidthDp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
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
    val offlineOnly by viewModel.offlineOnly.collectAsStateWithLifecycle()
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
    // The curated reading lists, including the "Want to read" pile.
    val readingLists: ReadingListStore = koinInject()
    val allReadingLists by readingLists.all.collectAsStateWithLifecycle(initialValue = emptyList())
    LaunchedEffect(Unit) { readingLists.ensureWantToRead() }
    // Notifications need permission on Android 13 and later. Ask when the user first subscribes.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LoadView(state, onRetry = viewModel::load) { page ->
            val summary = page.detail.summary
            val readable = remember(page.chapters) { page.chapters.filter { it.externalUrl == null } }
            // Blacklisted chapters stay out of the list until unblacklisted in settings.
            val blacklist: ChapterBlacklist = koinInject()
            val blacklistedIds by blacklist.blacklisted(summary.id).collectAsStateWithLifecycle(initialValue = emptySet())
            // The chapters as shown: filtered to unread or saved ones, and oldest first when chosen.
            val shown = remember(page.chapters, oldestFirst, unreadOnly, savedOnly, offlineOnly, lastRead?.chapterNumber, downloaded, blacklistedIds) {
                var list = page.chapters.withoutBlacklisted(blacklistedIds)
                if (unreadOnly) list = list.filter { it.externalUrl == null && !isChapterRead(it.number, lastRead?.chapterNumber) }
                if (savedOnly || offlineOnly) list = list.filter { it.id in downloaded }
                if (oldestFirst) list.asReversed() else list
            }
            // Collapsible volume groups, and the flat list of what the LazyColumn shows for jump indexing.
            val collapse = rememberCollapsedVolumes()
            // Trope tags mapped from the MangaDex tags, for discovery by trope.
            val groups = remember(shown) { toVolumeGroups(groupByVolume(shown)) }
            val flatSlots = remember(groups, collapse.collapsed) {
                buildList {
                    for (g in groups) {
                        if (g.label.isNotEmpty()) add(ChapterListItem.VolumeHeader(g.label))
                        if (g.label.isEmpty() || !collapse.isCollapsed(g.label)) {
                            g.chapters.forEach { add(ChapterListItem.Entry(it)) }
                        }
                    }
                }
            }
            // The chapters the list actually shows: collapsed volumes contribute none.
            // Shift-range selection uses this so it never picks hidden chapters.
            val visibleChapters = remember(flatSlots) {
                flatSlots.filterIsInstance<ChapterListItem.Entry>().map { it.chapter }
            }
            val nextExpected = remember(page.chapters, page.detail.status) { nextChapterEstimate(page.chapters.map { it.publishedAt }, page.detail.status) }
            // Oldest first and the saved filter need the whole list, not only the newest pages.
            LaunchedEffect(oldestFirst, savedOnly, offlineOnly, page.hasMore) { if ((oldestFirst || savedOnly || offlineOnly) && page.hasMore) viewModel.loadAll() }
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
                else context.openLink(link)
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
                    onOpenLink = { url -> context.openLink(url) },
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
                            sharedKey = summary.id,
                            modifier = Modifier.fillMaxSize().clickable(enabled = summary.coverUrl != null, onClickLabel = "Open the cover") { coverOpen = true },
                        )
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.6f), MaterialTheme.colorScheme.background),
                                ),
                            ),
                        )
                        // Top bar: home and info. Share lives in the More menu below.
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
                        }
                        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp)) {
                            // A long title shrinks to fit the cover area instead of pushing the details off it.
                            FitText(summary.title, MaterialTheme.typography.headlineLargeEmphasized, maxLines = 3, color = MaterialTheme.colorScheme.onBackground, minSize = 18.sp)
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
                            // Genre, follows, and rating join the facts line instead of taking rows of their own.
                            val stats = listOfNotNull(
                                summary.genre?.takeIf { it.isNotBlank() },
                                factsLine(page.detail).takeIf { it.isNotBlank() },
                                summary.follows?.let { "♥ ${compact(it)}" },
                                page.detail.rating?.let { "★ %.2f".format(Locale.US, it) },
                            ).joinToString(" · ")
                            Text(stats, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            nextExpected?.let { day ->
                                Text(
                                    if (day.isAfter(LocalDate.now())) "Next chapter likely around ${day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}" else "Next chapter expected any day",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                // The read button sits right under the series facts.
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
                        // Everything else sits behind one More button, so the page leads with reading and subscribing.
                        val wantToRead = allReadingLists.firstOrNull { it.id == WANT_TO_READ_LIST_ID }
                        val inWantToRead = wantToRead?.entries?.any { it.seriesId == summary.id } == true
                        var moreMenu by remember { mutableStateOf(false) }
                        Box {
                            OutlinedIconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (inWantToRead) "Remove from Want to read" else "Want to read") },
                                    onClick = {
                                        moreMenu = false
                                        haptics.performHapticFeedback(if (inWantToRead) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                                        scope.launch {
                                            val pile = readingLists.ensureWantToRead()
                                            if (inWantToRead) readingLists.removeSeries(pile.id, summary.id)
                                            else readingLists.addSeries(pile.id, summary.id, summary.title, summary.coverUrl)
                                        }
                                    },
                                )
                                DropdownMenuItem(text = { Text(status?.let { "List: ${it.label}" } ?: "Add to list") }, onClick = { moreMenu = false; statusMenu = true })
                                DropdownMenuItem(text = { Text(if (note.isBlank()) "Add note" else "Edit note") }, onClick = { moreMenu = false; editNote = true })
                                if (signedIn) {
                                    DropdownMenuItem(text = { Text(rating?.let { "Rated $it" } ?: "Rate") }, onClick = { moreMenu = false; rate = true })
                                }
                                DropdownMenuItem(text = { Text("Save chapters") }, onClick = { moreMenu = false; downloadMenu = true })
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.share)) },
                                    onClick = {
                                        moreMenu = false
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "${summary.title}\nhttps://mangadex.org/title/${summary.id}")
                                        }
                                        context.startActivity(Intent.createChooser(send, null))
                                    },
                                )
                            }
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
                groups.forEach { group ->
                    if (group.label.isNotEmpty()) {
                        item(key = "volume-${group.label}") { VolumeGroupHeader(group, collapse) }
                    }
                    // A collapsed volume hides its chapters; the header above stays to reopen it.
                    val chapters = if (group.label.isNotEmpty() && collapse.isCollapsed(group.label)) emptyList() else group.chapters
                    items(
                        chapters,
                        // Chapter rows recycle separately from volume headers, so a scrolled-off row is reused for another row.
                        key = { chapter -> chapter.id },
                        contentType = { 0 },
                    ) { chapter ->
                        val readable = chapter.externalUrl == null
                        val previous = previousOf[chapter.id]
                        EpisodeRow(
                            chapter,
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
                                // Picks every visible chapter between the last one tapped and this one.
                                // Chapters hidden in collapsed volumes stay unpicked, like the list shows.
                                val from = visibleChapters.indexOfFirst { it.id == anchor }.takeIf { it >= 0 }
                                    ?: visibleChapters.indexOfFirst { it.id == chapter.id }
                                val to = visibleChapters.indexOfFirst { it.id == chapter.id }
                                visibleChapters.subList(minOf(from, to), maxOf(from, to) + 1)
                                    .filter { it.externalUrl == null }
                                    .forEach { picked.add(it.id) }
                                anchor = chapter.id
                            },
                            onStartSelecting = {
                                selecting = true
                                picked.add(chapter.id)
                                anchor = chapter.id
                            },
                            onComments = { viewModel.openComments(chapter) { url -> context.openLink(url) } },
                            onBlacklist = {
                                scope.launch {
                                    blacklist.add(summary.id, chapter.id)
                                    viewModel.notifyBlacklisted(chapter)
                                }
                            },
                        )
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
            LaunchedEffect(pendingJump, flatSlots) {
                val wanted = pendingJump ?: return@LaunchedEffect
                val at = flatSlots.indexOfFirst { it is ChapterListItem.Entry && (it.chapter.number == wanted || it.chapter.number.toDoubleOrNull() == wanted.toDoubleOrNull()) }
                when {
                    at >= 0 -> {
                        // Items before the chapters: the header rows on a narrow screen, then the controls.
                        val before = if (wide) 0 else headerItemCount(offlineSavedAt != null, note.isNotBlank(), bookmarks.isNotEmpty(), summary.description.isNotBlank(), page.detail.tags.isNotEmpty(), related.isNotEmpty(), similar.isNotEmpty(), page.chapters.isEmpty() && !page.hasMore)
                        listState.animateScrollToItem(before + 1 + at)
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

/**
 * How many list items come before the chapters on a narrow screen: the cover, the read button, the
 * buttons row, and the optional sections. It must match the header built above.
 */
private fun headerItemCount(offline: Boolean, note: Boolean, bookmarks: Boolean, description: Boolean, tags: Boolean, related: Boolean, similar: Boolean, noChapters: Boolean): Int =
    listOf(offline, true, true, true, note, bookmarks, description, tags, related, similar, noChapters).count { it }
