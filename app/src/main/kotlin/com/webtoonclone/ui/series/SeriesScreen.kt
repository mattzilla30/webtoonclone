package com.webtoonclone.ui.series

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val progress by viewModel.progress.collectAsState()
    val subscribed by viewModel.subscribed.collectAsState()
    var showInfo by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LoadView(state, onRetry = viewModel::load) { page ->
            val summary = page.detail.summary
            val readable = page.chapters.filter { it.externalUrl == null }
            val first = readable.firstOrNull()
            val resume = progress?.let { p -> readable.find { it.id == p.chapterId } }
            val open: (Chapter) -> Unit = { chapter ->
                val link = chapter.externalUrl
                if (link == null) onOpenChapter(chapter.id)
                else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            }

            if (showInfo) InfoDialog(page.detail.status, summary.description, summary.author) { showInfo = false }

            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Box(Modifier.fillMaxWidth().height(340.dp)) {
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
                            Text(summary.title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 2)
                            Text(summary.author.orEmpty(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                summary.description,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.padding(top = 6.dp),
                            )
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
                item {
                    val target = resume ?: first
                    if (target == null && page.chapters.isNotEmpty()) {
                        Text(
                            "This series is hosted by its publisher. Episodes open in your browser.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    if (target != null) {
                        Box(
                            Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(22.dp)).background(Green)
                                .clickable { open(target) }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (resume != null) "Continue Ep. ${resume.number}" else "Episode ${target.number}",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                items(page.chapters.asReversed(), key = { it.id }) { chapter ->
                    EpisodeRow(chapter, page.chapters.indexOf(chapter) + 1, summary.coverUrl) { open(chapter) }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun EpisodeRow(chapter: Chapter, position: Int, coverUrl: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(coverUrl, null, Modifier.size(width = 56.dp, height = 56.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                buildString {
                    append("Ep. ${chapter.number}")
                    if (chapter.title.isNotBlank()) append(" · ${chapter.title}")
                    if (chapter.externalUrl != null) append("  ↗")
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(formatDate(chapter.publishedAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("#$position", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
