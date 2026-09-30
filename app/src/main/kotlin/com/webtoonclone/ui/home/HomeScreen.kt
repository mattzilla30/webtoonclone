package com.webtoonclone.ui.home

import kotlin.time.Duration.Companion.seconds
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.minutes
import com.webtoonclone.data.SeriesSummary
import com.webtoonclone.ui.Cover
import com.webtoonclone.ui.GenreLabel
import com.webtoonclone.ui.LoadView
import com.webtoonclone.ui.PickTile
import com.webtoonclone.ui.SectionHeader
import com.webtoonclone.ui.genreColor
import com.webtoonclone.ui.theme.Green
import com.webtoonclone.ui.timeAgo
import java.time.Instant

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSeries: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenChapter: (seriesId: String, chapterId: String) -> Unit,
    openCount: Int,
) {
    val state by viewModel.state.collectAsState()
    val recent by viewModel.recent.collectAsState()
    val offlineSavedAt by viewModel.offlineSavedAt.collectAsState()
    val showHint by viewModel.showHint.collectAsState()
    val subscribedIds by viewModel.subscribedIds.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val haptic = LocalHapticFeedback.current

    // Notifications need permission on Android 13 and later. Ask the first time you subscribe.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val toggleSubscribe: (SeriesSummary) -> Unit = { series ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (series.id !in subscribedIds) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        viewModel.toggleSubscribe(series)
    }

    // A new app open reloads with fresh random picks. Returning from a series page does not.
    LaunchedEffect(openCount) { viewModel.refreshIfNewOpen(openCount) }

    // While the home screen is visible, check every five minutes for newly started series.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(5.minutes)
                viewModel.refreshNewSeries()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
    LoadView(state, onRetry = viewModel::retry) { home ->
        LazyColumn(Modifier.fillMaxSize()) {
            offlineSavedAt?.let { savedAt ->
                item {
                    Text(
                        "Offline. Showing home saved ${timeAgo(Instant.ofEpochMilli(savedAt))}. Tap to retry.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { viewModel.retry() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            home.hero?.let { hero -> item { Hero(hero, onOpenSearch) { onOpenSeries(hero.id) } } }

            if (showHint) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(16.dp),
                    ) {
                        Text("Never miss a chapter", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "Tap Subscribe on a series page. The app checks every 30 minutes and notifies you when a new chapter comes out.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                        Text(
                            "Got it",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Green,
                            modifier = Modifier.clickable { viewModel.dismissHint() },
                        )
                    }
                }
            }

            if (recent.isNotEmpty()) {
                item { SectionHeader("Continue Reading") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(recent, key = { it.id }) { saved ->
                            Column(Modifier.width(100.dp).clickable { onOpenChapter(saved.id, saved.chapterId!!) }) {
                                Cover(saved.coverUrl, saved.title, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                                Text(saved.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                                saved.chapterNumber?.let {
                                    Text("Ep. $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            item { SectionHeader("New Series") }
            items(home.newSeries.size) { i ->
                val series = home.newSeries[i]
                NewSeriesRow(series, onClick = { onOpenSeries(series.id) }, onLongClick = { toggleSubscribe(series) })
            }

            item { SectionHeader("Today's Picks") }
            items(home.picks.chunked(2).size) { row ->
                Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    home.picks.chunked(2)[row].forEach { series ->
                        PickTile(
                            series,
                            { onOpenSeries(series.id) },
                            Modifier.weight(1f),
                            subscribed = series.id in subscribedIds,
                            onLongClick = { toggleSubscribe(series) },
                        )
                    }
                }
            }


            item { Box(Modifier.height(24.dp)) }
        }
    }

    toast?.let { message ->
        LaunchedEffect(message) {
            delay(3.seconds)
            viewModel.clearToast()
        }
        Text(
            message,
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
    }
}

@Composable
private fun Hero(series: SeriesSummary, onSearch: () -> Unit, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(360.dp).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick)) {
        Cover(series.coverUrl, series.title, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))),
            ),
        )
        Icon(
            Icons.Default.Search,
            contentDescription = "Search",
            tint = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(26.dp).clickable(onClick = onSearch),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(series.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                series.description,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NewSeriesRow(series: SeriesSummary, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 16.dp, vertical = 6.dp).height(84.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            GenreLabel(series.genre)
            Text(series.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                series.description,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Cover(series.coverUrl, series.title, Modifier.width(56.dp).fillMaxHeight().clip(RoundedCornerShape(4.dp)))
    }
}
