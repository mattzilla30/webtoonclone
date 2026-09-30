package com.webtoonclone.ui.series

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import com.webtoonclone.data.Chapter
import com.webtoonclone.data.SeriesDetail
import com.webtoonclone.data.languageName
import com.webtoonclone.ui.ChoiceChip
import com.webtoonclone.ui.Cover
import com.webtoonclone.ui.GenreLabel
import com.webtoonclone.ui.LoadView
import com.webtoonclone.ui.compact
import com.webtoonclone.ui.formatChapterDate
import com.webtoonclone.ui.theme.Green
import com.webtoonclone.ui.timeAgo
import java.util.Locale

@Composable
fun SeriesScreen(
    viewModel: SeriesViewModel,
    onOpenChapter: (chapterId: String) -> Unit,
    onHome: () -> Unit,
    onOpenTag: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val notifyEnabled by viewModel.notifyEnabled.collectAsState()
    val lastRead by viewModel.lastRead.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState()
    val offlineSavedAt by viewModel.offlineSavedAt.collectAsState()
    val subscribed by viewModel.subscribed.collectAsState()
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

            if (showInfo) {
                InfoDialog(
                    page.detail,
                    onOpenLink = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) },
                    onDismiss = { showInfo = false },
                )
            }

            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                offlineSavedAt?.let { savedAt ->
                    item {
                        Text(
                            "Offline. Showing a copy saved ${timeAgo(java.time.Instant.ofEpochMilli(savedAt))}. Tap to retry.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { viewModel.load() }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                item {
                    Box(Modifier.fillMaxWidth().height(340.dp).background(Color(0xFF2A2A2A))) {
                        Cover(summary.coverUrl, summary.title, Modifier.fillMaxSize())
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    listOf(Color(0x66000000), Color(0x99181818), Color(0xFF181818)),
                                ),
                            ),
                        )
                        // Top bar: home, subscribe, info, share.
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White, modifier = Modifier.clickable(onClick = onHome))
                            Spacer(Modifier.weight(1f))
                            Text(
                                if (subscribed) "Subscribed" else "+ Subscribe",
                                color = if (subscribed) Color.Black else Green,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .then(if (subscribed) Modifier.background(Green) else Modifier.border(1.dp, Green, RoundedCornerShape(14.dp)))
                                    .clickable {
                                        if (!subscribed) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        viewModel.toggleSubscribed(page.detail)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                            if (subscribed) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = if (notifyEnabled) "Notifications on for this series" else "Notifications off for this series",
                                    tint = if (notifyEnabled) Green else Color(0xFF777777),
                                    modifier = Modifier.padding(start = 16.dp).clickable { viewModel.setNotify(!notifyEnabled) },
                                )
                            }
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White, modifier = Modifier.padding(start = 16.dp).clickable { showInfo = true })
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White,
                                modifier = Modifier.padding(start = 16.dp).clickable {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "https://mangadex.org/title/${summary.id}")
                                    }
                                    context.startActivity(Intent.createChooser(send, null))
                                },
                            )
                        }
                        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp)) {
                            GenreLabel(summary.genre)
                            Text(summary.title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Text(summary.author.orEmpty(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                summary.follows?.let {
                                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Green, modifier = Modifier.size(12.dp))
                                    Text(" ${compact(it)}   ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                page.detail.rating?.let {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Green, modifier = Modifier.size(12.dp))
                                    Text(" %.2f".format(Locale.US, it), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                item {
                    if (lastRead == null && startAt == null && page.chapters.isNotEmpty()) {
                        Text(
                            "This series is hosted by its publisher. Episodes open in your browser.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    val resumeId = lastRead?.chapterId
                    if (resumeId != null || startAt != null) {
                        Box(
                            Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(22.dp)).background(Green)
                                .clickable { if (resumeId != null) onOpenChapter(resumeId) else open(startAt!!) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                when {
                                    resumeId != null -> "Continue Ep. ${lastRead?.chapterNumber}"
                                    page.hasMore -> "Latest Ep. ${startAt!!.number}"
                                    else -> "Episode ${startAt!!.number}"
                                },
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                items(page.chapters, key = { it.id }) { chapter ->
                    val readable = chapter.externalUrl == null
                    val previous = previousReadable(page.chapters, chapter)
                    EpisodeRow(
                        chapter,
                        summary.coverUrl,
                        read = isChapterRead(chapter.number, lastRead?.chapterNumber),
                        onClick = { open(chapter) },
                        // Read marks apply to chapters that open in the reader.
                        onMarkRead = if (readable) ({ viewModel.markReadUpTo(chapter, page.detail) }) else null,
                        // Marking unread needs an earlier chapter to fall back to, or the full list.
                        onMarkUnread = if (readable && (previous != null || !page.hasMore)) ({ viewModel.markUnreadFrom(previous, page.detail) }) else null,
                    )
                }
                if (loadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
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
            fontSize = 13.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
            onTextLayout = { if (!expanded) cutOff = it.hasVisualOverflow },
        )
        if (cutOff || expanded) {
            Text(
                if (expanded) "Show less" else "Read more",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Green,
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
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .alpha(if (read) 0.5f else 1f)
                .combinedClickable(onClick = onClick, onLongClick = if (onMarkRead != null) ({ menu = true }) else null)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(coverUrl, null, Modifier.width(40.dp).aspectRatio(2f / 3f))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    buildString {
                        append("Ep. ${chapter.number}")
                        if (chapter.title.isNotBlank()) append(" · ${chapter.title}")
                        if (chapter.externalUrl != null) append("  ↗")
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(formatChapterDate(chapter.publishedAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (onMarkRead != null) {
                DropdownMenuItem(text = { Text("Mark read up to here") }, onClick = { menu = false; onMarkRead() })
            }
            if (onMarkUnread != null) {
                DropdownMenuItem(text = { Text("Mark unread from here") }, onClick = { menu = false; onMarkUnread() })
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
private fun InfoDialog(detail: SeriesDetail, onOpenLink: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(detail.status.uppercase(), color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            val facts = listOfNotNull(
                detail.year?.toString(),
                detail.demographic,
                languageName(detail.originalLanguage).takeIf { it.isNotEmpty() },
            )
            if (facts.isNotEmpty()) {
                Text(facts.joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            Text(detail.summary.description, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
            if (!detail.summary.author.isNullOrBlank()) {
                Text("Written by", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(detail.summary.author, fontSize = 13.sp)
            }
            if (detail.altTitles.isNotEmpty()) {
                Text("Also known as", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                detail.altTitles.forEach { Text(it, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp)) }
            }
            if (detail.links.isNotEmpty()) {
                Text("Links", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                detail.links.forEach { link ->
                    Text(link.label, fontSize = 13.sp, color = Green, modifier = Modifier.clickable { onOpenLink(link.url) }.padding(vertical = 4.dp))
                }
            }
        }
    }
}
