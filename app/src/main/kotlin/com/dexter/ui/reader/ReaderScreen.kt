package com.dexter.ui.reader

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.BatteryManager
import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import com.dexter.data.PageFit
import com.dexter.data.PageTransition
import com.dexter.data.ReaderBackground
import com.dexter.data.ReaderOrientation
import com.dexter.data.ReadingMode
import com.dexter.data.Settings
import com.dexter.data.TapAction
import com.dexter.data.pagesOfSpread
import com.dexter.data.spreadCount
import com.dexter.data.tapAction
import com.dexter.ui.Load
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
import java.util.Date
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.seconds

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
private const val AUTO_HIDE_MS = 4_000L
private const val READING_TICK_MS = 15_000L
private const val CLOCK_TICK_MS = 30_000L
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
    onOpenChapter: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val chosenMode by viewModel.chosenMode.collectAsStateWithLifecycle()
    val hasSeriesLook by viewModel.hasSeriesLook.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
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

    // The reader's own brightness, or the phone's when it follows the system. The phone's comes back when the reader closes.
    DisposableEffect(activity, settings.readerBrightness) {
        val window = activity?.window
        window?.attributes = window.attributes.apply {
            screenBrightness = if (settings.readerBrightness in 1..100) {
                settings.readerBrightness / 100f
            } else {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
        onDispose {
            window?.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
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

    Box(
        Modifier.fillMaxSize().background(readerBackgroundColor(settings.readerBackground))
            // Loading and error text keeps clear of the system bars. The pages themselves go edge to edge.
            .then(if (state is Load.Ready) Modifier else Modifier.systemBarsPadding()),
    ) {
        LoadView(state, onRetry = viewModel::retry) { page ->
            ReaderContent(
                viewModel = viewModel,
                page = page,
                settings = settings,
                mode = mode,
                zoom = zoom,
                optionsOpen = showOptions,
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

        toast?.let { message ->
            LaunchedEffect(message) {
                delay(3.seconds)
                viewModel.clearToast()
            }
            Snackbar(modifier = Modifier.align(Alignment.TopCenter).systemBarsPadding().padding(16.dp)) { Text(message) }
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
    optionsOpen: Boolean,
    onOpenChapter: (String) -> Unit,
    onBack: () -> Unit,
    onOpenOptions: () -> Unit,
) {
    val paged = mode != ReadingMode.Vertical
    val rtl = mode == ReadingMode.PagedRtl
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var barsVisible by remember { mutableStateOf(true) }
    // Bumped on every touch of the bars, so auto-hide waits for you to stop using them.
    var barTouch by remember { mutableIntStateOf(0) }
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()

    // Fullscreen: the status and navigation bars hide with the reader's own bars. A swipe from an edge shows
    // them for a moment, and they come back for good when the reader closes.
    val window = (LocalActivity.current)?.window
    DisposableEffect(window, view) {
        onDispose { window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) } }
    }
    LaunchedEffect(window, barsVisible) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) } ?: return@LaunchedEffect
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (barsVisible) controller.show(WindowInsetsCompat.Type.systemBars()) else controller.hide(WindowInsetsCompat.Type.systemBars())
    }
    var showChapters by remember { mutableStateOf(false) }
    var jumpTo by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<Cursor?>(null) }
    var container by remember { mutableStateOf(IntSize.Zero) }
    val onPage = if (settings.readerBackground == ReaderBackground.White) Color.Black else Color.White
    val colorFilter = remember(settings.readerFilter) { readerColorFilter(settings.readerFilter) }

    // With the setting on, the bars hide a few seconds after they show, unless a sheet or dialog is open.
    LaunchedEffect(barsVisible, settings.autoHideBars, optionsOpen, showChapters, jumpTo, menuFor, barTouch) {
        if (!barsVisible || !settings.autoHideBars || optionsOpen || showChapters || jumpTo != null || menuFor != null) return@LaunchedEffect
        delay(AUTO_HIDE_MS)
        barsVisible = false
    }

    // In the vertical strip, continuous reading joins the chapters that follow onto the end.
    val continuous = !paged && settings.continuousScroll
    val segments = if (continuous) page.segments else page.segments.take(1)
    val strip = remember(segments) { buildStrip(segments) }
    val currentStrip by rememberUpdatedState(strip)
    val currentSegments by rememberUpdatedState(segments)

    // The place on screen. When the mode changes, the newly shown layout is moved to it first, and then follows it.
    val startPage = page.startPage.coerceIn(0, (page.first.pages.size - 1).coerceAtLeast(0))
    var cursor by remember { mutableStateOf(Cursor(0, startPage)) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = stripIndexOf(strip, 0, startPage))

    // Paged mode shows one chapter at a time: the one you were in when it opened.
    val pagedSegment = remember(paged) { cursor.segment.coerceIn(0, page.segments.lastIndex) }
    val pagedChapter = page.segments[pagedSegment]
    val pagedCount = pagedChapter.pages.size
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val spreads = paged && settings.spreads && landscape
    // The pager has one extra page after the last image, for the end-of-chapter card.
    val pagerCount = (if (spreads) spreadCount(pagedCount) else pagedCount) + 1
    val pagerState = rememberPagerState(initialPage = pagerIndexOf(startPage, spreads)) { pagerCount }

    val current = page.segments.getOrElse(cursor.segment) { page.first }
    val count = current.pages.size
    val lastIndex = (count - 1).coerceAtLeast(0)
    val position = cursor.page.coerceIn(0, lastIndex)

    LaunchedEffect(paged, spreads, listState, pagerState) {
        if (paged) {
            pagerState.scrollToPage(pagerIndexOf(cursor.page, spreads).coerceAtMost(pagerCount - 1))
            snapshotFlow { pagerState.currentPage }.collect { index ->
                cursor = Cursor(pagedSegment, pageOfPager(index, pagedCount, spreads).coerceIn(0, (pagedCount - 1).coerceAtLeast(0)))
            }
        } else {
            listState.scrollToItem(stripIndexOf(strip, cursor.segment, cursor.page))
            snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
                cursor = cursorAt(currentStrip, index) { s -> currentSegments.getOrNull(s)?.pages?.size ?: 0 }
            }
        }
    }

    // A chapter coming on screen counts as read, and may save or delete its neighbours.
    LaunchedEffect(cursor.segment) { page.segments.getOrNull(cursor.segment)?.let(viewModel::enterSegment) }

    // A new page or a new mode starts fully zoomed out.
    LaunchedEffect(cursor, paged) { zoom.reset() }

    // How far down the page on screen you are, from 0 to 1. A tall webtoon page needs it to resume in place.
    val currentPaged by rememberUpdatedState(paged)
    fun fractionOnScreen(): Float {
        if (currentPaged) return 0f
        val item = currentStrip.getOrNull(listState.firstVisibleItemIndex) as? StripItem.Page ?: return 0f
        if (item.segment != cursor.segment || item.page != cursor.page) return 0f
        val height = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == listState.firstVisibleItemIndex }?.size ?: return 0f
        return if (height > 0) listState.firstVisibleItemScrollOffset.toFloat() / height else 0f
    }

    fun save(at: Cursor, fraction: Float) {
        val segment = page.segments.getOrNull(at.segment) ?: return
        viewModel.saveProgress(segment.chapter.id, at.page, fraction, segment.pages.size)
    }

    // True until the reader is back at your place within the start page. Saving waits, so the old place is not lost to "top of page".
    var restoring by remember { mutableStateOf(!paged && page.startFraction > 0f) }

    // Progress is saved once scrolling pauses, and once more when the reader closes, so fast scrolling does not write for every page.
    LaunchedEffect(Unit) {
        try {
            snapshotFlow { if (restoring) null else cursor to (fractionOnScreen() * 1000).toInt() }
                .filterNotNull()
                .distinctUntilChanged()
                .debounce(SAVE_PROGRESS_DELAY_MS)
                .collect { (at, permille) -> save(at, permille / 1000f) }
        } finally {
            if (restoring && cursor == Cursor(0, startPage)) save(cursor, page.startFraction) else save(cursor, fractionOnScreen())
        }
    }

    // Back to where you were within the start page. Its height is known once its image loads, so this waits for that.
    val placeholderPx = with(LocalDensity.current) { PLACEHOLDER_HEIGHT.roundToPx() }
    LaunchedEffect(Unit) {
        if (!restoring) return@LaunchedEffect
        val start = stripIndexOf(strip, 0, startPage)
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

    // Time on screen counts toward the reading stats while the reader is in front.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val readingChapter by rememberUpdatedState(current.chapter.id)
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                delay(READING_TICK_MS)
                viewModel.addReadingTime(readingChapter, READING_TICK_MS)
            }
        }
    }

    // Fetch the next few pages ahead of the reader so they are ready when you arrive.
    LaunchedEffect(strip, paged, settings.cropBorders) {
        val loader = SingletonImageLoader.get(context)
        snapshotFlow { cursor }.distinctUntilChanged().collect { at ->
            val urls = if (paged) {
                (at.page + 1..at.page + PRELOAD_AHEAD).mapNotNull { pagedChapter.pages.getOrNull(it) }
            } else {
                val index = stripIndexOf(strip, at.segment, at.page)
                (index + 1..index + PRELOAD_AHEAD).mapNotNull { i ->
                    (strip.getOrNull(i) as? StripItem.Page)?.let { segments.getOrNull(it.segment)?.pages?.getOrNull(it.page) }
                }
            }
            urls.forEach { url -> loader.enqueue(pageRequest(context, url, settings.cropBorders)) }
        }
    }

    // Near the end of a chapter, continuous reading joins the next one on. Otherwise its first pages load ahead.
    LaunchedEffect(segments.size, continuous, settings.prefetchPages, pagedSegment) {
        val last = if (continuous) segments.last() else if (paged) pagedChapter else segments.first()
        val lastSegment = page.segments.indexOf(last)
        snapshotFlow { cursor.segment == lastSegment && cursor.page >= last.pages.size - NEXT_CHAPTER_PRELOAD_AT }.first { it }
        if (continuous) {
            viewModel.appendNext()
        } else if (settings.prefetchPages > 0) {
            val loader = SingletonImageLoader.get(context)
            viewModel.nextChapterPreview(settings.prefetchPages, last).forEach { url -> loader.enqueue(pageRequest(context, url, settings.cropBorders)) }
        }
    }

    // Tap navigation in paged mode slides, fades, or jumps, as set.
    suspend fun PagerState.turnTo(index: Int) {
        val target = index.coerceIn(0, pagerCount - 1)
        if (settings.pageTransition == PageTransition.Slide) animateScrollToPage(target) else scrollToPage(target)
    }

    // Volume keys turn a page in paged mode and scroll most of a screen in the vertical strip.
    LaunchedEffect(paged, listState, pagerState) {
        VolumeKeyPager.events.collect { direction ->
            if (paged) {
                pagerState.turnTo(pagerState.currentPage + direction)
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
        val target = index.coerceIn(0, lastIndex)
        if (paged) pagerState.scrollToPage(pagerIndexOf(target, spreads)) else listState.scrollToItem(stripIndexOf(strip, cursor.segment, target))
        cursor = Cursor(cursor.segment, target)
    }

    /** The page under [y] in the strip, or the one on screen in paged mode. */
    fun pageAt(y: Float): Cursor {
        if (paged || zoom.isZoomed) return cursor
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { y.toInt() in it.offset until it.offset + it.size } ?: return cursor
        return (strip.getOrNull(item.index) as? StripItem.Page)?.let { Cursor(it.segment, it.page) } ?: cursor
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { container = it }
            .pointerInput(paged, rtl, settings.tapToScroll, pagerCount) {
                detectTapGestures(
                    onTap = { offset ->
                        if (!paged) {
                            val third = size.height / 3f
                            when {
                                settings.tapToScroll && offset.y < third ->
                                    scope.launch { listState.animateScrollBy(pageScrollAmount(listState.layoutInfo.viewportSize.height, -1)) }
                                settings.tapToScroll && offset.y > 2 * third ->
                                    scope.launch { listState.animateScrollBy(pageScrollAmount(listState.layoutInfo.viewportSize.height, 1)) }
                                else -> barsVisible = !barsVisible
                            }
                        } else {
                            when (tapAction(offset.x, size.width.toFloat(), rtl)) {
                                TapAction.ToggleBars -> barsVisible = !barsVisible
                                TapAction.Next -> scope.launch { pagerState.turnTo(pagerState.currentPage + 1) }
                                TapAction.Previous -> scope.launch { pagerState.turnTo(pagerState.currentPage - 1) }
                            }
                        }
                    },
                    onDoubleTap = { offset ->
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        zoom.toggle(offset, container)
                    },
                    onLongPress = { offset ->
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        menuFor = pageAt(offset.y)
                    },
                )
            }
            .zoomGestures(zoom) { container }
            // Taps and swipes turn pages by sight. TalkBack gets the same moves as actions.
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Next page") {
                        scope.launch {
                            if (paged) pagerState.turnTo(pagerState.currentPage + 1) else listState.animateScrollBy(pageScrollAmount(listState.layoutInfo.viewportSize.height, 1))
                        }
                        true
                    },
                    CustomAccessibilityAction("Previous page") {
                        scope.launch {
                            if (paged) pagerState.turnTo(pagerState.currentPage - 1) else listState.animateScrollBy(pageScrollAmount(listState.layoutInfo.viewportSize.height, -1))
                        }
                        true
                    },
                    CustomAccessibilityAction(if (barsVisible) "Hide controls" else "Show controls") {
                        barsVisible = !barsVisible
                        true
                    },
                    CustomAccessibilityAction("Page options") {
                        menuFor = cursor
                        true
                    },
                )
            },
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
                    val fade = settings.pageTransition == PageTransition.Fade
                    val layer = if (fade) {
                        Modifier.graphicsLayer {
                            // Each page stays in place and fades, instead of sliding.
                            val offset = (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction
                            translationX = offset * size.width * (if (rtl) -1 else 1)
                            alpha = 1f - offset.absoluteValue.coerceIn(0f, 1f)
                        }
                    } else {
                        Modifier
                    }
                    Box(Modifier.fillMaxSize().then(layer)) {
                        if (index < pagerCount - 1) {
                            val shown = if (spreads) pagesOfSpread(index, pagedCount) else listOf(index)
                            PagedPage(pagedChapter, shown, rtl, settings, colorFilter, viewModel::renewPages)
                        } else {
                            EndOfChapter(
                                segment = pagedChapter,
                                textColor = onPage,
                                continuing = false,
                                onOpenChapter = onOpenChapter,
                                onComments = { openComments(viewModel, context, pagedChapter) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.align(Alignment.TopCenter).widthIn(max = MAX_STRIP_WIDTH).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(settings.pageGap.dp),
                ) {
                    // Keyed by chapter and page, so new page addresses and joined chapters keep your place.
                    items(strip, key = { it.key }, contentType = { it::class }) { item ->
                        when (item) {
                            is StripItem.Page -> {
                                val segment = segments[item.segment]
                                PageImage(
                                    url = segment.pages[item.page],
                                    index = item.page,
                                    layout = PageLayout.Strip,
                                    crop = settings.cropBorders,
                                    colorFilter = colorFilter,
                                    onGaveUp = { viewModel.renewPages(segment.chapter.id) },
                                )
                            }
                            is StripItem.Divider -> ChapterDivider(segments[item.segment], onPage)
                            is StripItem.End -> {
                                val segment = segments[item.segment]
                                EndOfChapter(
                                    segment = segment,
                                    textColor = onPage,
                                    continuing = continuous && segment.nextId != null && segments.size < MAX_SEGMENTS,
                                    onOpenChapter = onOpenChapter,
                                    onComments = { openComments(viewModel, context, segment) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dimming sits over the pages and under the bars. It does not take touches.
        if (settings.readerDim > 0) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = settings.readerDim.coerceIn(0, 70) / 100f)))
        }

        if (!barsVisible) {
            // A small counter stays visible when the bars are hidden, with the time and battery when that is on.
            val status by clockAndBattery(context, settings.showClock)
            Surface(
                shape = CircleShape,
                color = barColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(12.dp),
            ) {
                Text(
                    listOfNotNull("${position + 1} / $count", status).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        if (barsVisible) {
            ReaderTopBar(
                segment = current,
                seriesTitle = page.seriesTitle,
                bookmarked = bookmarks.any { it.chapterId == current.chapter.id && it.page == position },
                incognito = settings.incognito,
                onBookmark = {
                    barTouch++
                    viewModel.toggleBookmark(current.chapter, position)
                },
                onBack = onBack,
                onOpenOptions = onOpenOptions,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            ReaderBottomBar(
                segment = current,
                position = position,
                count = count,
                rtl = rtl,
                onSeek = {
                    barTouch++
                    scope.launch { goToPage(it) }
                },
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

        menuFor?.let { at ->
            val segment = page.segments.getOrNull(at.segment)
            val url = segment?.pages?.getOrNull(at.page)
            if (segment == null || url == null) {
                menuFor = null
            } else {
                PageMenu(
                    pageNumber = at.page + 1,
                    bookmarked = bookmarks.any { it.chapterId == segment.chapter.id && it.page == at.page },
                    onSave = { viewModel.savePage(url, segment.chapter, at.page) },
                    onShare = { viewModel.sharePage(url, segment.chapter, at.page) { context.startActivity(it) } },
                    onCopy = { viewModel.copyPage(url, segment.chapter, at.page) },
                    onBookmark = { viewModel.toggleBookmark(segment.chapter, at.page) },
                    onDismiss = { menuFor = null },
                )
            }
        }

        if (showChapters) {
            ChapterPicker(
                chapters = page.chapters,
                currentId = current.chapter.id,
                onSelect = {
                    showChapters = false
                    if (it != current.chapter.id) onOpenChapter(it)
                },
                onDismiss = { showChapters = false },
            )
        }
    }
}

private fun openComments(viewModel: ReaderViewModel, context: Context, segment: ChapterSegment) {
    viewModel.openComments(segment.chapter) { url -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}

/** The time and battery level, refreshed every half minute, or null when [enabled] is off. */
@Composable
private fun clockAndBattery(context: Context, enabled: Boolean) = produceState<String?>(null, enabled) {
    if (!enabled) {
        value = null
        return@produceState
    }
    val battery = context.getSystemService(BatteryManager::class.java)
    while (isActive) {
        val time = DateFormat.getTimeFormat(context).format(Date())
        val level = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
        value = if (level != null) "$time  ·  $level%" else time
        delay(CLOCK_TICK_MS)
    }
}

/** How a page image is laid out: across the strip's width, fitted whole, or at the screen's height. */
private enum class PageLayout { Strip, Screen, Height }

/** One paged-mode page, or two side by side in a spread, fitted as the page-fit setting says. */
@Composable
private fun PagedPage(
    segment: ChapterSegment,
    shown: List<Int>,
    rtl: Boolean,
    settings: Settings,
    colorFilter: ColorFilter?,
    onGaveUp: (String) -> Unit,
) {
    val renew = { onGaveUp(segment.chapter.id) }
    if (shown.size > 1) {
        // A spread reads in the chapter's direction, so right to left puts the earlier page on the right.
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            (if (rtl) shown.reversed() else shown).forEach { index ->
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    PageImage(segment.pages[index], index, PageLayout.Screen, settings.cropBorders, colorFilter, renew)
                }
            }
        }
        return
    }
    val index = shown.firstOrNull() ?: return
    val url = segment.pages[index]
    when (settings.pageFit) {
        PageFit.Screen -> PageImage(url, index, PageLayout.Screen, settings.cropBorders, colorFilter, renew)
        PageFit.Width -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            PageImage(url, index, PageLayout.Strip, settings.cropBorders, colorFilter, renew)
        }
        PageFit.Height -> Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            PageImage(url, index, PageLayout.Height, settings.cropBorders, colorFilter, renew)
        }
    }
}

/**
 * One page image. A failed load retries twice on its own, then asks for new page addresses through
 * [onGaveUp] and shows a box that retries when tapped. In the strip the image fills the width at its
 * natural height. Fitted to the screen, it shows whole. At the screen's height, it may run off the sides.
 */
@Composable
private fun PageImage(url: String, index: Int, layout: PageLayout, crop: Boolean, colorFilter: ColorFilter?, onGaveUp: () -> Unit) {
    val context = LocalPlatformContext.current
    // Bumping the attempt count rebuilds the image, which asks the server again.
    var attempt by remember { mutableIntStateOf(0) }
    // Two quiet retries with a growing pause before the tap-to-retry message shows. New addresses start over.
    var autoRetries by remember(url) { mutableIntStateOf(0) }
    var failed by remember(url) { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val request = remember(url, crop) { pageRequest(context, url, crop) }
    val size = when (layout) {
        PageLayout.Strip -> Modifier.fillMaxWidth()
        PageLayout.Screen -> Modifier.fillMaxSize()
        PageLayout.Height -> Modifier.fillMaxHeight()
    }
    Box(
        size
            // Until it loads, a page in the strip holds a placeholder height. The image itself stays unbounded, so it decodes at full height.
            .then(if (layout != PageLayout.Strip || loaded) Modifier else Modifier.heightIn(min = if (failed) 200.dp else PLACEHOLDER_HEIGHT))
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
                contentScale = when (layout) {
                    PageLayout.Strip -> ContentScale.FillWidth
                    PageLayout.Screen -> ContentScale.Fit
                    PageLayout.Height -> ContentScale.FillHeight
                },
                colorFilter = colorFilter,
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

/** The heading between two chapters in a continuous strip. */
@Composable
private fun ChapterDivider(segment: ChapterSegment, textColor: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalDivider(color = textColor.copy(alpha = 0.3f))
        Text(
            buildString {
                append("Ep. ${segment.chapter.number}")
                if (segment.chapter.title.isNotBlank()) append(" · ${segment.chapter.title}")
            },
            color = textColor,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        HorizontalDivider(color = textColor.copy(alpha = 0.3f))
    }
}

/**
 * The card after a chapter. With [continuing], the next chapter is on its way into the strip, so the card
 * says so. Otherwise it offers the next episode.
 */
@Composable
private fun EndOfChapter(
    segment: ChapterSegment,
    textColor: Color,
    continuing: Boolean,
    onOpenChapter: (String) -> Unit,
    onComments: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("End of Ep. ${segment.chapter.number}", color = textColor, style = MaterialTheme.typography.titleMediumEmphasized)
        when {
            continuing -> Text("Loading the next episode...", color = textColor, modifier = Modifier.padding(top = 16.dp))
            segment.nextId != null -> Button(
                onClick = { onOpenChapter(segment.nextId) },
                modifier = Modifier.padding(top = 16.dp).heightIn(min = ButtonDefaults.MediumContainerHeight),
            ) { Text("Next episode") }
            else -> Text("You are caught up", color = textColor, modifier = Modifier.padding(top = 16.dp))
        }
        OutlinedButton(onClick = onComments, modifier = Modifier.padding(top = 12.dp)) { Text("Comments") }
    }
}

/** What a long press on a page offers: save it, share it, copy it, or bookmark it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageMenu(
    pageNumber: Int,
    bookmarked: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onBookmark: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Page $pageNumber", style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        listOf(
            "Save to Pictures" to onSave,
            "Share" to onShare,
            "Copy" to onCopy,
            (if (bookmarked) "Remove bookmark" else "Bookmark this page") to onBookmark,
        ).forEach { (label, action) ->
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        action()
                        onDismiss()
                    }
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
        Box(Modifier.padding(bottom = 24.dp))
    }
}
