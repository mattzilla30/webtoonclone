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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.dexter.R
import com.dexter.data.SeriesSummary
import com.dexter.ui.Cover
import com.dexter.ui.GenreLabel
import com.dexter.ui.Load
import com.dexter.ui.LoadView
import com.dexter.ui.OfflineBanner
import com.dexter.ui.PickTile
import com.dexter.ui.SectionHeader
import com.dexter.ui.adaptiveColumns
import com.dexter.ui.windowWidthDp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSeries: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenChapter: (seriesId: String, chapterId: String) -> Unit,
    openCount: Int,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
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
        PullToRefreshBox(isRefreshing = state is Load.Loading, onRefresh = viewModel::retry, modifier = Modifier.fillMaxSize()) {
            LoadView(state, onRetry = viewModel::retry) { home ->
                LazyColumn(Modifier.fillMaxSize()) {
                    offlineSavedAt?.let { savedAt ->
                        item {
                            OfflineBanner(savedAt, "home", onRetry = { viewModel.retry() })
                        }
                    }
                    home.hero?.let { hero -> item { Hero(hero, onOpenSearch, onShuffle = viewModel::retry) { onOpenSeries(hero.id) } } }

                    if (recent.isNotEmpty()) {
                        item { SectionHeader("Continue Reading") }
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
                                Box(Modifier.maskClip(MaterialTheme.shapes.large).clickable { onOpenChapter(saved.id, saved.chapterId!!) }) {
                                    Cover(saved.coverUrl, saved.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))))
                                    Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                                        Text(saved.title, color = Color.White, style = MaterialTheme.typography.labelLargeEmphasized)
                                        saved.chapterNumber?.let { Text("Ep. $it", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall) }
                                    }
                                }
                            }
                        }
                    }

                    because?.let { (title, like) ->
                        item { SectionHeader("Because you read $title") }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(like, key = { it.id }) { series ->
                                    PickTile(series, { onOpenSeries(series.id) }, Modifier.width(110.dp), onLongClick = { toggleSubscribe(series) })
                                }
                            }
                        }
                    }

                    item { SectionHeader("New Series") }
                    items(home.newSeries, key = { it.id }) { series ->
                        NewSeriesRow(series, onClick = { onOpenSeries(series.id) }, onLongClick = { toggleSubscribe(series) })
                    }

                    item { SectionHeader("Today's Picks") }
                    val pickRows = home.picks.chunked(columns)
                    items(pickRows, key = { it.first().id }) { rowSeries ->
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

@Composable
private fun Hero(series: SeriesSummary, onSearch: () -> Unit, onShuffle: () -> Unit, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(16.dp).height(380.dp).clip(MaterialTheme.shapes.extraLarge).clickable(onClick = onClick),
    ) {
        Box {
            Cover(series.coverUrl, series.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000)))))
            Row(Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(onClick = onShuffle) {
                    Icon(Icons.Default.Refresh, contentDescription = "Shuffle picks")
                }
                FilledTonalIconButton(onClick = onSearch) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
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
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
