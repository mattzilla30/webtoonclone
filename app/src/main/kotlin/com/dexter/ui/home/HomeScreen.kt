package com.dexter.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.dexter.R
import com.dexter.data.ReadingProgress
import com.dexter.data.SavedSeries
import com.dexter.data.SeriesSummary
import com.dexter.ui.Cover
import com.dexter.ui.FitText
import com.dexter.ui.GenreLabel
import com.dexter.ui.Load
import com.dexter.ui.LoadView
import com.dexter.ui.OfflineBanner
import com.dexter.ui.PickTile
import com.dexter.ui.SectionHeader
import com.dexter.ui.adaptiveColumns
import com.dexter.ui.rowTileWidth
import com.dexter.ui.windowWidthDp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** "3 new", or "New" when the number of new chapters cannot be counted. */
fun newLabel(count: Int?): String = if (count != null) "$count new" else "New"

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSeries: (String) -> Unit,
    onOpenChapter: (seriesId: String, chapterId: String) -> Unit,
    onBrowse: (label: String) -> Unit,
    openCount: Int,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val newCounts by viewModel.newCounts.collectAsStateWithLifecycle()
    val fromSubscriptions by viewModel.fromSubscriptions.collectAsStateWithLifecycle()
    val showTip by viewModel.showLongPressTip.collectAsStateWithLifecycle()
    val because by viewModel.becauseYouRead.collectAsStateWithLifecycle()
    val appContext = LocalContext.current
    val columns = adaptiveColumns(windowWidthDp())
    LaunchedEffect(Unit) { viewModel.refreshBecause() }
    val offlineSavedAt by viewModel.offlineSavedAt.collectAsStateWithLifecycle()
    val subscribedIds by viewModel.subscribedIds.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
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

    // Load the covers further down the page ahead of the scroll.
    val loaded = (state as? Load.Ready)?.value
    LaunchedEffect(loaded) {
        if (loaded != null) {
            val loader = SingletonImageLoader.get(appContext)
            (loaded.newSeries + loaded.picks).mapNotNull { it.coverUrl }.forEach { loader.enqueue(ImageRequest.Builder(appContext).data(it).build()) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // A pull keeps what is on screen and swaps in the new picks when they arrive.
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
            LoadView(state, onRetry = viewModel::retry) { home ->
                // Sideways rows size their tiles to the screen, so the spacing stays even on any width.
                val tileWidth = rowTileWidth(windowWidthDp()).dp
                LazyColumn(Modifier.fillMaxSize()) {
                    offlineSavedAt?.let { savedAt ->
                        item {
                            OfflineBanner(savedAt, "home", onRetry = { viewModel.retry() })
                        }
                    }
                    home.hero?.let { hero ->
                        item {
                            Hero(hero) { onOpenSeries(hero.id) }
                        }
                    }

                    if (recent.isNotEmpty()) {
                        item(contentType = "header") { SectionHeader("Continue Reading") }
                        item {
                            val carousel = rememberCarouselState { recent.size }
                            HorizontalUncontainedCarousel(
                                state = carousel,
                                itemWidth = 140.dp,
                                itemSpacing = 8.dp,
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                modifier = Modifier.fillMaxWidth().height(250.dp),
                            ) { index ->
                                val saved = recent[index]
                                ContinueCard(
                                    saved = saved,
                                    progress = progress[saved.id]?.takeIf { it.chapterId == saved.chapterId },
                                    newCount = newCounts[saved.id],
                                    hasNew = saved.id in newCounts,
                                    modifier = Modifier.maskClip(MaterialTheme.shapes.large),
                                    onOpen = { onOpenChapter(saved.id, saved.chapterId!!) },
                                    onOpenSeries = { onOpenSeries(saved.id) },
                                    onMarkRead = { viewModel.markCaughtUp(saved.id) },
                                    onRemove = { viewModel.removeFromHistory(saved.id) },
                                )
                            }
                        }
                    }

                    if (fromSubscriptions.isNotEmpty()) {
                        item(contentType = "header") { SectionHeader("From your subscriptions") }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(fromSubscriptions, key = { it.series.id }) { unread ->
                                    UnreadTile(unread, tileWidth) { onOpenChapter(unread.series.id, unread.series.knownChapterId!!) }
                                }
                            }
                        }
                    }

                    because?.let { (title, like) ->
                        item(contentType = "header") { SectionHeader("Because you read $title") }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(like, key = { it.id }) { series ->
                                    PickTile(series, { onOpenSeries(series.id) }, Modifier.width(tileWidth), subscribed = series.id in subscribedIds, onLongClick = { toggleSubscribe(series) })
                                }
                            }
                        }
                    }

                    item(contentType = "header") { SectionHeader("New Series", onClick = { onBrowse("Recently added") }) }
                    items(home.newSeries, key = { it.id }, contentType = { "new-series" }) { series ->
                        NewSeriesRow(series, onClick = { onOpenSeries(series.id) }, onLongClick = { toggleSubscribe(series) })
                    }

                    item(contentType = "header") { SectionHeader("Today's Picks", onClick = { onBrowse("Popular") }) }
                    if (showTip) {
                        item(contentType = "tip") { LongPressTip(onDismiss = viewModel::dismissLongPressTip) }
                    }
                    val pickRows = home.picks.chunked(columns)
                    items(pickRows, key = { it.first().id }, contentType = { "picks" }) { rowSeries ->
                        Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowSeries.forEach { series ->
                                PickTile(
                                    series,
                                    { onOpenSeries(series.id) },
                                    Modifier.weight(1f),
                                    subscribed = series.id in subscribedIds,
                                    onLongClick = { toggleSubscribe(series) },
                                )
                            }
                            repeat(columns - rowSeries.size) { Box(Modifier.weight(1f)) }
                        }
                    }

                    item { Box(Modifier.height(24.dp)) }
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

/** One Continue Reading cover: where you are in the chapter, a badge for new chapters, and a long-press menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueCard(
    saved: SavedSeries,
    progress: ReadingProgress?,
    newCount: Int?,
    hasNew: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
    onOpenSeries: () -> Unit,
    onMarkRead: () -> Unit,
    onRemove: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    Box(
        modifier.combinedClickable(
            onClick = onOpen,
            onClickLabel = "Continue reading",
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menu = true
            },
            onLongClickLabel = "More",
        ),
    ) {
        Cover(saved.coverUrl, saved.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))))
        if (hasNew) {
            Text(
                newLabel(newCount),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp)) {
            Text(saved.title, color = Color.White, style = MaterialTheme.typography.labelLargeEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
            saved.chapterNumber?.let { Text("Ep. $it", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall) }
            progress?.share?.let { share ->
                LinearProgressIndicator(
                    progress = { share },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp)
                        .semantics { contentDescription = "${(share * 100).toInt()} percent read" },
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Continue reading") }, onClick = { menu = false; onOpen() })
            DropdownMenuItem(text = { Text("Open series page") }, onClick = { menu = false; onOpenSeries() })
            if (hasNew) DropdownMenuItem(text = { Text("Mark all read") }, onClick = { menu = false; onMarkRead() })
            DropdownMenuItem(text = { Text("Remove from history") }, onClick = { menu = false; onRemove() })
        }
    }
}

/** A subscribed series with unread chapters. Tapping opens its newest chapter. */
@Composable
private fun UnreadTile(unread: UnreadSeries, width: Dp, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick,
        modifier = Modifier.width(width),
    ) {
        Column {
            Box {
                Cover(unread.series.coverUrl, unread.series.title, Modifier.fillMaxWidth().aspectRatio(2f / 3f), contentScale = ContentScale.Crop, thumb = true)
                Text(
                    newLabel(unread.newCount),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            // A fixed-height text area keeps every tile in the row the same size.
            val type = MaterialTheme.typography
            val textHeight = with(LocalDensity.current) { type.labelLarge.lineHeight.toDp() * 2 + type.labelSmall.lineHeight.toDp() }
            Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp).height(textHeight)) {
                FitText(unread.series.title, type.labelLarge, Modifier.weight(1f))
                unread.series.knownChapterNumber?.let {
                    Text("Ep. $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A one-time tip that a long press on any cover subscribes. */
@Composable
private fun LongPressTip(onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Tip: press and hold any cover to subscribe.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Got it") }
        }
    }
}

@Composable
private fun Hero(
    series: SeriesSummary,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        // A minimum height, so a long title in a large font grows the card instead of being cut off.
        modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 380.dp).clip(MaterialTheme.shapes.extraLarge).clickable(onClick = onClick),
    ) {
        Box(Modifier.heightIn(min = 380.dp)) {
            Cover(series.coverUrl, series.title, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000)))))
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp).padding(top = 200.dp)) {
                Text(series.title, color = Color.White, style = MaterialTheme.typography.headlineLargeEmphasized)
                Text(
                    series.description,
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NewSeriesRow(series: SeriesSummary, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Subscribe or unsubscribe"),
    ) {
        Row(Modifier.padding(12.dp).heightIn(min = 92.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                GenreLabel(series.genre)
                Text(series.title, style = MaterialTheme.typography.titleSmallEmphasized)
                Text(
                    series.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Cover(series.coverUrl, series.title, Modifier.width(62.dp).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.small), contentScale = ContentScale.Crop)
        }
    }
}
