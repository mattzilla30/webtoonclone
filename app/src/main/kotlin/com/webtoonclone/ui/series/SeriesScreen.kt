package com.webtoonclone.ui.series

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.webtoonclone.data.Chapter
import com.webtoonclone.ui.Cover
import com.webtoonclone.ui.GenreLabel
import com.webtoonclone.ui.LoadView
import com.webtoonclone.ui.compact
import com.webtoonclone.ui.theme.Green
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SeriesScreen(
    viewModel: SeriesViewModel,
    onOpenChapter: (chapterId: String) -> Unit,
    onHome: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val lastRead by viewModel.lastRead.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState()
    val subscribed by viewModel.subscribed.collectAsState()
    var showInfo by remember { mutableStateOf(false) }
    val context = LocalContext.current

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
                else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            }

            if (showInfo) InfoDialog(page.detail.status, summary.description, summary.author) { showInfo = false }

            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                item {
                    Box(Modifier.fillMaxWidth().height(340.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Cover(summary.coverUrl, summary.title, Modifier.fillMaxSize())
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    listOf(Color(0x66000000), Color(0x99181818), MaterialTheme.colorScheme.background),
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
                                    .clickable { viewModel.toggleSubscribed(page.detail) }
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                            )
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
                    EpisodeRow(chapter, summary.coverUrl) { open(chapter) }
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
            color = Color.White.copy(alpha = 0.85f),
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

@Composable
private fun EpisodeRow(chapter: Chapter, coverUrl: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
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
            Text(formatDate(chapter.publishedAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoDialog(status: String, description: String, author: String?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(20.dp),
        ) {
            Text(status.uppercase(), color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(description, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
            if (!author.isNullOrBlank()) {
                Text("Written by", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(author, fontSize = 13.sp)
            }
        }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

private fun formatDate(iso: String): String =
    runCatching { OffsetDateTime.parse(iso).format(dateFormat) }.getOrDefault("")
