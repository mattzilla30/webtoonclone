package com.dexter.ui.reader

import android.content.pm.ActivityInfo
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import com.dexter.data.ReaderBackground
import com.dexter.data.ReaderOrientation
import com.dexter.data.ReadingMode
import com.dexter.data.Settings
import com.dexter.data.TapAction
import com.dexter.data.tapAction
import com.dexter.ui.LoadView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The color behind the pages, chosen in settings. */
fun readerBackgroundColor(background: ReaderBackground): Color = when (background) {
    ReaderBackground.Dark -> Color(0xFF181818)
    ReaderBackground.Black -> Color.Black
    ReaderBackground.White -> Color.White
}

private const val PRELOAD_AHEAD = 4
private const val AUTO_RETRIES = 2
private const val RETRY_BASE_MS = 800L
private const val NEXT_CHAPTER_PRELOAD_AT = 3
private const val SAVE_PROGRESS_DELAY_MS = 400L
private const val RESTORE_WAIT_MS = 5_000L
private const val FRAME_NANOS_60HZ = 16_666_667f
private val PLACEHOLDER_HEIGHT = 500.dp

/** On a tablet a vertical strip this wide reads better than one stretched across the screen. */
private val MAX_STRIP_WIDTH = 720.dp

/** Pixels scrolled per 60 Hz frame at each auto-scroll level. Level 0 is off. */
private val AUTO_SCROLL_PX = floatArrayOf(0f, 1.5f, 3f, 5f, 8f, 12f)

/** The translucent panel colour behind the reader's bars. */
@Composable
internal fun barColor() = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    seriesId: String,
    onOpenChapter: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val chosenMode by viewModel.chosenMode.collectAsStateWithLifecycle()
    val hasSeriesLook by viewModel.hasSeriesLook.collectAsStateWithLifecycle()
    var showOptions by remember { mutableStateOf(false) }
    val zoom = remember { ZoomState() }

    // Lock the screen direction while the reader is open, and give it back when it closes.
    val activity = LocalActivity.current
    DisposableEffect(activity, settings.readerOrientation) {
        activity?.requestedOrientation = when (settings.readerOrientation) {
            ReaderOrientation.Auto -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ReaderOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ReaderOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    // Keep the screen on while reading, and release it when the reader closes.
    val view = LocalView.current
    DisposableEffect(view, settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // With the setting on, the volume keys turn pages or scroll instead of changing the volume.
    DisposableEffect(settings.volumeKeys) {
        VolumeKeyPager.active = settings.volumeKeys
        onDispose { VolumeKeyPager.active = false }
    }

    Box(Modifier.fillMaxSize().background(readerBackgroundColor(settings.readerBackground))) {
        LoadView(state, onRetry = viewModel::retry) { page ->
            ReaderContent(
                viewModel = viewModel,
                page = page,
                settings = settings,
                mode = mode,
                zoom = zoom,
                onOpenChapter = onOpenChapter,
                onBack = onBack,
                onOpenOptions = { showOptions = true },
            )
        }

        if (showOptions) {
            ReaderOptions(
                settings = settings,
                mode = mode,
                chosenMode = chosenMode,
                seriesLook = hasSeriesLook,
                onSeriesLook = viewModel::setSeriesLook,
                onChange = viewModel::updateSettings,
                onMode = viewModel::setMode,
                onDismiss = { showOptions = false },
            )
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun ReaderContent(
    viewModel: ReaderViewModel,
    page: ReaderPage,
    settings: Settings,
    mode: ReadingMode,
    zoom: ZoomState,
    onOpenChapter: (String) -> Unit,
    onBack: () -> Unit,
    onOpenOptions: () -> Unit,
) {
    val paged = mode != ReadingMode.Vertical
    val rtl = mode == ReadingMode.PagedRtl
    val count = page.pages.size
    val lastIndex = (count - 1).coerceAtLeast(0)
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var barsVisible by remember { mutableStateOf(true) }
    var showChapters by remember { mutableStateOf(false) }
    var jumpTo by remember { mutableStateOf<String?>(null) }
    var container by remember { mutableStateOf(IntSize.Zero) }
    val onPage = if (settings.readerBackground == ReaderBackground.White) Color.Black else Color.White

    val start = page.startPage.coerceIn(0, lastIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = start)
    // The pager has one extra page after the last image, for the end-of-chapter card.
    val pagerState = rememberPagerState(initialPage = start) { count + 1 }

    // The page on screen, 0-based. When the mode changes, the newly shown layout is moved to the
    // current page first, and then follows it.
    var position by remember { mutableIntStateOf(start) }
    LaunchedEffect(paged, listState, pagerState) {
        if (paged) pagerState.scrollToPage(position) else listState.scrollToItem(position)
        snapshotFlow { if (paged) pagerState.currentPage else listState.firstVisibleItemIndex }
            .collect { position = it.coerceAtMost(lastIndex) }
    }

    // A new page or a new mode starts fully zoomed out.
    LaunchedEffect(position, paged) { zoom.reset() }

    // How far down the page on screen you are, from 0 to 1. A tall webtoon page needs it to resume in place.
    val currentPaged by rememberUpdatedState(paged)
    fun fractionOnScreen(): Float {
        if (currentPaged || listState.firstVisibleItemIndex != position) return 0f
        val height = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == position }?.size ?: return 0f
        return if (height > 0) listState.firstVisibleItemScrollOffset.toFloat() / height else 0f
    }

    // True until the reader is back at your place within the start page. Saving waits, so the old place is not lost to "top of page".
    var restoring by remember { mutableStateOf(!paged && page.startFraction > 0f) }

    // Progress is saved once scrolling pauses, and once more when the reader closes, so fast scrolling does not write for every page.
    LaunchedEffect(Unit) {
        try {
            snapshotFlow { if (restoring) null else position to (fractionOnScreen() * 1000).toInt() }
                .filterNotNull()
                .distinctUntilChanged()
                .debounce(SAVE_PROGRESS_DELAY_MS)
                .collect { (index, permille) -> viewModel.saveProgress(index, permille / 1000f) }
        } finally {
            if (restoring && position == start) viewModel.saveProgress(start, page.startFraction) else viewModel.saveProgress(position, fractionOnScreen())
        }
    }

    // Back to where you were within the start page. Its height is known once its image loads, so this waits for that.
    val placeholderPx = with(LocalDensity.current) { PLACEHOLDER_HEIGHT.roundToPx() }
    LaunchedEffect(Unit) {
        if (!restoring) return@LaunchedEffect
        val height = withTimeoutOrNull(RESTORE_WAIT_MS) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == start }?.size ?: 0 }
                .first { it > 0 && it != placeholderPx }
        }
        // Only when you have not scrolled in the meantime.
        if (height != null && listState.firstVisibleItemIndex == start && listState.firstVisibleItemScrollOffset == 0) {
            listState.scrollToItem(start, (height * page.startFraction).toInt())
        }
        // Left unset when the reader closes first, so the closing save keeps the old place.
        restoring = false
    }

    // Fetch the next few pages ahead of the reader so they are ready when you arrive.
    LaunchedEffect(page.pages) {
        val loader = SingletonImageLoader.get(context)
        snapshotFlow { position }.distinctUntilChanged().collect { first ->
            for (next in first + 1..first + PRELOAD_AHEAD) {
                page.pages.getOrNull(next)?.let { url -> loader.enqueue(pageRequest(context, url)) }
            }
        }
    }

    // Near the end of the chapter, preload the first pages of the next one.
    LaunchedEffect(page.pages, settings.prefetchPages) {
        if (settings.prefetchPages <= 0) return@LaunchedEffect
        snapshotFlow { position >= count - NEXT_CHAPTER_PRELOAD_AT }.first { it }
        val loader = SingletonImageLoader.get(context)
        viewModel.nextChapterPreview(settings.prefetchPages).forEach { url -> loader.enqueue(pageRequest(context, url)) }
    }

    // Volume keys turn a page in paged mode and scroll most of a screen in the vertical strip.
    LaunchedEffect(paged, listState, pagerState) {
        VolumeKeyPager.events.collect { direction ->
            if (paged) {
                pagerState.animateScrollToPage((pagerState.currentPage + direction).coerceIn(0, count))
            } else {
                listState.animateScrollBy(pageScrollAmount(listState.layoutInfo.viewportSize.height, direction))
            }
        }
    }

    // Auto-scroll runs in the vertical strip. A touch takes the scroll over, and then it switches itself off.
    val level = settings.autoScrollLevel.coerceIn(0, AUTO_SCROLL_PX.lastIndex)
    LaunchedEffect(listState, level, paged) {
        if (level == 0 || paged) return@LaunchedEffect
        try {
            var lastFrame = 0L
            while (isActive) {
                if (!listState.canScrollForward) {
                    viewModel.updateSettings { it.copy(autoScrollLevel = 0) }
                    break
                }
                // One step per frame, sized by the time since the last one, so the speed holds on 60 and 120 Hz screens.
                val now = withFrameNanos { it }
                val frames = if (lastFrame == 0L) 1f else ((now - lastFrame) / FRAME_NANOS_60HZ).coerceAtMost(4f)
                lastFrame = now
                listState.scrollBy(AUTO_SCROLL_PX[level] * frames)
            }
        } catch (e: CancellationException) {
            if (isActive) viewModel.updateSettings { it.copy(autoScrollLevel = 0) }
            throw e
        }
    }

    suspend fun goToPage(index: Int) {
        position = index.coerceIn(0, lastIndex)
        if (paged) pagerState.scrollToPage(position) else listState.scrollToItem(position)
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { container = it }
            .pointerInput(paged, rtl) {
                detectTapGestures(
                    onTap = { offset ->
                        if (!paged) {
                            barsVisible = !barsVisible
                        } else {
                            when (tapAction(offset.x, size.width.toFloat(), rtl)) {
                                TapAction.ToggleBars -> barsVisible = !barsVisible
                                TapAction.Next -> scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(count)) }
                                TapAction.Previous -> scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) }
                            }
                        }
                    },
                    onDoubleTap = { offset ->
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        zoom.toggle(offset, container)
                    },
                )
            }
            .zoomGestures(zoom) { container },
    ) {
        // The pages and only the pages are zoomed. The bars and dimming stay put.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offsetX
                    translationY = zoom.offsetY
                },
        ) {
            if (paged) {
                HorizontalPager(
                    state = pagerState,
                    reverseLayout = rtl,
                    userScrollEnabled = !zoom.isZoomed,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize(),
                ) { index ->
                    if (index < count) {
                        PageImage(page.pages[index], index, fill = true, onGaveUp = viewModel::renewPages)
                    } else {
                        EndOfChapter(page, onPage, onOpenChapter, Modifier.fillMaxSize())
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.align(Alignment.TopCenter).widthIn(max = MAX_STRIP_WIDTH).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(settings.pageGap.dp),
                ) {
                    // Keyed by position, so new page addresses keep your place.
                    itemsIndexed(page.pages) { index, url -> PageImage(url, index, fill = false, onGaveUp = viewModel::renewPages) }
                    item { EndOfChapter(page, onPage, onOpenChapter, Modifier.fillMaxWidth()) }
                }
            }
        }

        // Dimming sits over the pages and under the bars. It does not take touches.
        if (settings.readerDim > 0) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = settings.readerDim.coerceIn(0, 70) / 100f)))
        }

        if (!barsVisible) {
            // A small counter stays visible when the bars are hidden.
            Surface(
                shape = CircleShape,
                color = barColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            ) {
                Text("${position + 1} / $count", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }

        if (barsVisible) {
            ReaderTopBar(page, onBack, onOpenOptions, Modifier.align(Alignment.TopCenter))
            ReaderBottomBar(
                page = page,
                position = position,
                count = count,
                rtl = rtl,
                onSeek = { scope.launch { goToPage(it) } },
                onJump = { jumpTo = (position + 1).toString() },
                onChapters = { showChapters = true },
                onOpenChapter = onOpenChapter,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        jumpTo?.let { typed ->
            GoToPageDialog(
                typed = typed,
                count = count,
                onChange = { jumpTo = it },
                onGo = { index -> scope.launch { goToPage(index) } },
                onDismiss = { jumpTo = null },
            )
        }

        if (showChapters) {
            ChapterPicker(
                chapters = page.chapters,
                currentId = page.chapter.id,
                onSelect = {
                    showChapters = false
                    if (it != page.chapter.id) onOpenChapter(it)
                },
                onDismiss = { showChapters = false },
            )
        }
    }
}

/**
 * One page image. A failed load retries twice on its own, then asks for new page addresses through
 * [onGaveUp] and shows a box that retries when tapped. In the vertical strip the image fills the width
 * at its natural height. In paged mode it is fitted whole into the screen.
 */
@Composable
private fun PageImage(url: String, index: Int, fill: Boolean, onGaveUp: () -> Unit) {
    val context = LocalPlatformContext.current
    // Bumping the attempt count rebuilds the image, which asks the server again.
    var attempt by remember { mutableIntStateOf(0) }
    // Two quiet retries with a growing pause before the tap-to-retry message shows. New addresses start over.
    var autoRetries by remember(url) { mutableIntStateOf(0) }
    var failed by remember(url) { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val request = remember(url) { pageRequest(context, url) }
    val size = if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
    Box(
        size
            // Until it loads, a page in the strip holds a placeholder height. The image itself stays unbounded, so it decodes at full height.
            .then(if (fill || loaded) Modifier else Modifier.heightIn(min = if (failed) 200.dp else PLACEHOLDER_HEIGHT))
            .then(if (loaded) Modifier else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh))
            .then(
                if (failed) {
                    Modifier.clickable {
                        autoRetries = 0
                        attempt++
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        key(attempt) {
            AsyncImage(
                model = request,
                contentDescription = "Page ${index + 1}",
                contentScale = if (fill) ContentScale.Fit else ContentScale.FillWidth,
                modifier = size,
                onSuccess = {
                    loaded = true
                    failed = false
                },
                onError = {
                    loaded = false
                    failed = true
                    if (autoRetries < AUTO_RETRIES) {
                        val wait = RETRY_BASE_MS shl autoRetries
                        autoRetries++
                        scope.launch {
                            delay(wait)
                            attempt++
                        }
                    } else {
                        onGaveUp()
                    }
                },
            )
        }
        if (failed) {
            Text(
                if (autoRetries < AUTO_RETRIES) "Retrying page ${index + 1}..." else "Page ${index + 1} failed to load. Tap to retry.",
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

@Composable
private fun EndOfChapter(page: ReaderPage, textColor: Color, onOpenChapter: (String) -> Unit, modifier: Modifier) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("End of Ep. ${page.chapter.number}", color = textColor, style = MaterialTheme.typography.titleMediumEmphasized)
        if (page.nextId != null) {
            Button(
                onClick = { onOpenChapter(page.nextId) },
                modifier = Modifier.padding(top = 16.dp).heightIn(min = ButtonDefaults.MediumContainerHeight),
            ) { Text("Next episode") }
        }
    }
}
