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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
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
import com.dexter.ui.CoverTile
import com.dexter.ui.FitText
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
    onOpenSearch: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenSettings: () -> Unit,
    /** Subscribed series with unread chapters, shown as a badge on the My Series button. */
    unread: Int,
    openCount: Int,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val newCounts by viewModel.newCounts.collectAsStateWithLifecycle()
    val fromSubscriptions by viewModel.fromSubscriptions.collectAsStateWithLifecycle()
    val because by viewModel.becauseYouRead.collectAsStateWithLifecycle()
    val appContext = LocalContext.current
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

    Column(Modifier.fillMaxSize()) {
        // Search, then My Series, Updates and Settings, in one row across the top.
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            HomeSearchBar(onOpenSearch, Modifier.weight(1f))
            IconButton(onClick = onOpenLibrary) {
                if (unread > 0) {
                    BadgedBox(badge = { Badge { Text(if (unread > 99) "99+" else unread.toString()) } }) {
                        Icon(Icons.Default.Favorite, contentDescription = stringResource(R.string.my_series))
                    }
                } else {
                    Icon(Icons.Default.Favorite, contentDescription = stringResource(R.string.my_series))
                }
            }
            IconButton(onClick = onOpenUpdates) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.updates)) }
            IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings)) }
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
                        item(contentType = "row") { PickRow(home.newSeries, tileWidth, subscribedIds, onOpenSeries, toggleSubscribe) }

                        item(contentType = "header") { SectionHeader("Random Picks", onClick = { onBrowse("Popular") }) }
                        // The random lead series opens the picks instead of taking a full-width card of its own.
                        item(contentType = "row") {
                            PickRow((listOfNotNull(home.hero) + home.picks).distinctBy { it.id }, tileWidth, subscribedIds, onOpenSeries, toggleSubscribe)
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
    CoverTile(
        unread.series.coverUrl,
        unread.series.title,
        Modifier.width(width).clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
        subtitle = unread.series.knownChapterNumber?.let { "Ep. $it" },
        topStart = {
            Text(
                newLabel(unread.newCount),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        },
    )
}

/** The search box at the top of Home. Tapping it opens the search page with the keyboard up. */
@Composable
private fun HomeSearchBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.search_series)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.heightIn(min = 52.dp).semantics { role = Role.Button },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

/** A sideways row of cover tiles, as New Series and Random Picks show them. Long-press subscribes. */
@Composable
private fun PickRow(
    series: List<SeriesSummary>,
    tileWidth: androidx.compose.ui.unit.Dp,
    subscribedIds: Set<String>,
    onOpenSeries: (String) -> Unit,
    onToggleSubscribe: (SeriesSummary) -> Unit,
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(series, key = { it.id }) { item ->
            PickTile(item, { onOpenSeries(item.id) }, Modifier.width(tileWidth), subscribed = item.id in subscribedIds, onLongClick = { onToggleSubscribe(item) })
        }
    }
}
