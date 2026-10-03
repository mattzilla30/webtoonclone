package com.dexter.ui.reader

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.Dp
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
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import com.dexter.cast.CastManager
import com.dexter.data.A11yPrefs
import com.dexter.data.A11yState
import com.dexter.data.PageFit
import com.dexter.data.PageSegment
import com.dexter.data.PageTransition
import com.dexter.data.PowerPrefs
import com.dexter.data.PowerState
import com.dexter.data.ReaderBackground
import com.dexter.data.ReaderOrientation
import com.dexter.data.ReaderUi
import com.dexter.data.ReaderUiPrefs
import com.dexter.data.ReadingMode
import com.dexter.data.Settings
import com.dexter.data.TapAction
import com.dexter.data.firstPageOfPair
import com.dexter.data.isColorful
import com.dexter.data.isSpreadAspect
import com.dexter.data.pairIndexOf
import com.dexter.data.pairPages
import com.dexter.data.splitTallPage
import com.dexter.data.tabletReadingMode
import com.dexter.platform.WearBridge
import com.dexter.tts.ReaderTtsService
import com.dexter.tts.TtsPageEvents
import com.dexter.ui.Load
import com.dexter.ui.LoadView
import com.dexter.ui.windowWidthDp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.compose.koinInject
import java.util.Date
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds

/** The color behind the pages, chosen in settings. */
fun readerBackgroundColor(background: ReaderBackground): Color = when (background) {
    ReaderBackground.Dark -> Color(0xFF181818)
    ReaderBackground.Black -> Color.Black
    ReaderBackground.White -> Color.White
}

/**
 * The mean colour of [bitmap], sampled from a tiny downscale so it costs almost nothing.
 * Feeds the smart background tint.
 */
private fun averageColor(bitmap: Bitmap): Color {
    // A hardware bitmap's pixels cannot be read; callers pass the page sample, which is software.
    if (bitmap.config == Bitmap.Config.HARDWARE) return Color.Transparent
    val w = bitmap.width.coerceAtLeast(1)
    val h = bitmap.height.coerceAtLeast(1)
    val scale = 24f / max(w, h)
    val sw = (w * scale).toInt().coerceAtLeast(1)
    val sh = (h * scale).toInt().coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
    val pixels = IntArray(sw * sh)
    small.getPixels(pixels, 0, sw, 0, 0, sw, sh)
    if (small !== bitmap) small.recycle()
    var r = 0L
    var g = 0L
    var b = 0L
    for (pixel in pixels) {
        r += (pixel shr 16) and 0xFF
        g += (pixel shr 8) and 0xFF
        b += pixel and 0xFF
    }
    val n = pixels.size.coerceAtLeast(1).toFloat()
    return Color(r / n / 255f, g / n / 255f, b / n / 255f)
}

/** Mixes [sampled] into [base] so a vivid page tints the background without shouting over the chrome. */
private fun blendColors(base: Color, sampled: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = base.red + (sampled.red - base.red) * t,
        green = base.green + (sampled.green - base.green) * t,
        blue = base.blue + (sampled.blue - base.blue) * t,
    )
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
    val zen by viewModel.zen.collectAsStateWithLifecycle()
    var showOptions by remember { mutableStateOf(false) }
    val zoom = remember { ZoomState() }
    // Spread-aware pairing shift, toggled from the reader options: 0 is the cover alone, 1 pairs it forward.
    var pairShift by remember { mutableIntStateOf(0) }

    // The batch-A reader UI preferences: toolbar layout, binge mode, stylus, device class, and more.
    val context = LocalContext.current
    val uiPrefs = remember { ReaderUiPrefs(context) }
    val readerUi by uiPrefs.ui.collectAsStateWithLifecycle(initialValue = ReaderUi())
    // Per-device-class reading mode: a series' own choice wins, otherwise a wide window reads paged.
    val widthDp = windowWidthDp()
    val readingMode = remember(mode, chosenMode, widthDp, readerUi) {
        effectiveReadingMode(chosenMode, mode, readerUi.deviceClassMode, widthDp, readerUi.tabletReadingMode())
    }

    // Lock the screen direction while the reader is open, and give it back when it closes.
    // A series with its own orientation wins over the global one.
    val activity = LocalActivity.current
    DisposableEffect(activity, settings.readerOrientation, settings.seriesOrientations) {
        val orientation = settings.seriesOrientations[viewModel.seriesId] ?: settings.readerOrientation
        activity?.requestedOrientation = when (orientation) {
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
    // Zen mode always routes navigation to the volume keys, even with the setting off.
    DisposableEffect(settings.volumeKeys, zen) {
        VolumeKeyPager.active = settings.volumeKeys || zen
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
                mode = readingMode,
                readerUi = readerUi,
                uiPrefs = uiPrefs,
                pairShift = pairShift,
                zoom = zoom,
                zen = zen,
                optionsOpen = showOptions,
                onOpenChapter = onOpenChapter,
                onBack = onBack,
                onOpenOptions = { showOptions = true },
            )
        }

        if (showOptions) {
            ReaderOptions(
                settings = settings,
                mode = readingMode,
                chosenMode = chosenMode,
                seriesLook = hasSeriesLook,
                onSeriesLook = viewModel::setSeriesLook,
                zen = zen,
                onZen = viewModel::setZen,
                seriesOrientation = settings.seriesOrientations[viewModel.seriesId] ?: ReaderOrientation.Auto,
                onSeriesOrientation = viewModel::setSeriesOrientation,
                onChange = viewModel::updateSettings,
                onMode = viewModel::setMode,
                onDismiss = { showOptions = false },
                pairShift = pairShift,
                onPairShift = { pairShift = (pairShift + 1) % 2 },
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
    readerUi: ReaderUi,
    uiPrefs: ReaderUiPrefs,
    /** Spread-aware pairing shift, toggled from the reader options. */
    pairShift: Int,
    zoom: ZoomState,
    zen: Boolean,
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
    val eInk = settings.eInkMode
    val reduceMotion = remember(context, settings) { reduceMotionEnabled(context, settings) }
    // Guided panel stepping keeps its own camera state for the page on screen.
    val guided = remember { GuidedState() }
    // Smart background: each sampled page colour, by strip item key or pager spread index.
    val smartColors = remember { mutableStateMapOf<String, Color>() }

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
    var ocrUrl by remember { mutableStateOf<String?>(null) }
    var showThumbnails by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showCastPicker by remember { mutableStateOf(false) }
    val topActions = remember(readerUi.topActionsCsv) { toolbarActionsOrDefault(readerUi.topActionsCsv, defaultTopActions) }
    val bottomActions = remember(readerUi.bottomActionsCsv) { toolbarActionsOrDefault(readerUi.bottomActionsCsv, defaultBottomActions) }
    var container by remember { mutableStateOf(IntSize.Zero) }
    val onPage = if (settings.readerBackground == ReaderBackground.White) Color.Black else Color.White
    // E-ink mode forces high contrast: no dimming and no colour filter over the pages.
    val colorFilter = remember(settings.readerFilter, eInk) {
        if (eInk) null else readerColorFilter(settings.readerFilter)
    }
    // Color page detection: pages found to be color skip the dimming and greyscale night filters.
    val colorPages = remember { mutableStateMapOf<String, Boolean>() }
    val reportPageColor: ((String, Boolean) -> Unit)? =
        if (readerUi.colorPageExempt) { key, colorful -> colorPages[key] = colorful } else null

    /** The filter for one page: color pages are exempt when the setting is on. */
    fun filterFor(key: String?): ColorFilter? =
        if (readerUi.colorPageExempt && key != null && colorPages[key] == true) null else colorFilter

    // With the setting on, the bars hide a few seconds after they show, unless a sheet or dialog is open.
    LaunchedEffect(barsVisible, settings.autoHideBars, optionsOpen, showChapters, jumpTo, menuFor, barTouch) {
        if (!barsVisible || !settings.autoHideBars || optionsOpen || showChapters || jumpTo != null || menuFor != null) return@LaunchedEffect
        delay(AUTO_HIDE_MS)
        barsVisible = false
    }

    // In the vertical strip, continuous reading joins the chapters that follow onto the end.
    val continuous = !paged && settings.continuousScroll
    val segments = if (continuous) page.segments else page.segments.take(1)
    // Power-user prefs: gamepad page turning lives here.
    val powerPrefs = remember { PowerPrefs(context) }
    val power by powerPrefs.state.collectAsStateWithLifecycle(initialValue = PowerState())
    // Accessibility prefs: TTS, voice control, and tall-page splitting.
    val a11yPrefs = remember { A11yPrefs(context) }
    val a11y by a11yPrefs.state.collectAsStateWithLifecycle(initialValue = A11yState())
    // Tall-page splitting: a page's decoded height lands here, and pages taller than the setting
    // split into chunks, each its own strip item that decodes only its crop.
    val pageHeights = remember { mutableStateMapOf<String, Int>() }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val maxSplitHeightPx = remember(configuration, density, a11y.tallSplitScreens) {
        (configuration.screenHeightDp * density.density * a11y.tallSplitScreens).toInt().coerceAtLeast(1024)
    }
    val splits: Map<Pair<Int, Int>, List<PageSegment>> = remember(segments, paged, a11y.tallPageSplit, maxSplitHeightPx, pageHeights.toMap()) {
        if (paged || !a11y.tallPageSplit) emptyMap()
        else buildMap {
            segments.forEachIndexed { s, segment ->
                segment.pages.forEachIndexed { p, url ->
                    val height = pageHeights[url] ?: return@forEachIndexed
                    val parts = splitTallPage(height, maxSplitHeightPx)
                    if (parts.size > 1) put(s to p, parts)
                }
            }
        }
    }
    val strip = remember(segments, splits) { buildStrip(segments, splits) }
    // Strip styling from the reader UI settings: the gap, the page corner rounding, the background.
    val stripGap = readerUi.stripGapDp.takeIf { it >= 0 } ?: settings.pageGap
    val stripBgChoice = parseStripBackground(readerUi.stripBg)
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
    // Spread-aware pairing: pages detected as wide (two-page spreads) keep a full-width slot while
    // the rest pair up. Detections land as pages load, so the pairs recompute then.
    val spreadFlags = remember { mutableStateMapOf<String, Boolean>() }
    val pairs: List<List<Int>> = remember(pagedChapter, pagedCount, spreads, readerUi.spreadAware, pairShift, spreadFlags.toList()) {
        if (!spreads) {
            emptyList()
        } else {
            pairPages(
                pagedCount,
                isSpread = { index -> readerUi.spreadAware && spreadFlags[pagedChapter.pages.getOrNull(index)] == true },
                shift = pairShift,
            )
        }
    }

    /** The pager page that shows [page], with spread-aware pairing. */
    fun pagerIndexOfPage(page: Int): Int = if (spreads) pairIndexOf(pairs, page).takeIf { it >= 0 } ?: 0 else page

    /** The first page the pager page at [index] shows, with spread-aware pairing. */
    fun firstPageOfPairIndex(index: Int): Int = if (spreads) firstPageOfPair(pairs, index, pagedCount) else index
    // The pager has one extra page after the last image, for the end-of-chapter card.
    val pagerCount = (if (spreads) pairs.size else pagedCount) + 1
    val pagerState = rememberPagerState(initialPage = pagerIndexOfPage(startPage)) { pagerCount }

    // A newly detected spread re-pairs the pages; stay on the pair holding the page you were on.
    LaunchedEffect(pairs) {
        if (!spreads || pairs.isEmpty()) return@LaunchedEffect
        val target = pairIndexOf(pairs, cursor.page).takeIf { it >= 0 } ?: return@LaunchedEffect
        cursor = Cursor(pagedSegment, firstPageOfPairIndex(target).coerceIn(0, (pagedCount - 1).coerceAtLeast(0)))
        if (target != pagerState.currentPage) pagerState.scrollToPage(target.coerceAtMost(pagerCount - 1))
    }

    // Each page's aspect ratio, reported as it loads, feeds the spread detection above.
    val reportAspect: ((String, Float) -> Unit)? =
        if (spreads && readerUi.spreadAware) { url, aspect -> spreadFlags[url] = isSpreadAspect(aspect) } else null

    val current = page.segments.getOrElse(cursor.segment) { page.first }
    val count = current.pages.size
    val lastIndex = (count - 1).coerceAtLeast(0)
    val position = cursor.page.coerceIn(0, lastIndex)

    // Chromecast: when a session starts, send the chapter to the TV; keep the TV on the visible page.
    val castManager: CastManager = koinInject()
    val isCasting by castManager.isCasting.collectAsStateWithLifecycle()
    LaunchedEffect(isCasting, current.chapter.id) {
        if (isCasting) {
            castManager.startChapter(
                pages = current.pages,
                startIndex = position,
                title = page.seriesTitle ?: "Dexter",
                subtitle = "Ep. ${current.chapter.number}",
            )
        }
    }
    LaunchedEffect(position) {
        if (castManager.isCasting.value) castManager.seekToPage(position)
    }

    LaunchedEffect(paged, spreads, listState, pagerState) {
        if (paged) {
            pagerState.scrollToPage(pagerIndexOfPage(cursor.page).coerceAtMost(pagerCount - 1))
            snapshotFlow { pagerState.currentPage }.collect { index ->
                cursor = Cursor(pagedSegment, firstPageOfPairIndex(index).coerceIn(0, (pagedCount - 1).coerceAtLeast(0)))
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

    /** True while guided panel stepping owns the camera instead of whole-page turns. */
    val guidedStepping = paged && settings.guidedPanels

    /** Moves the camera to the guided region: an instant cut with reduce motion, an ease otherwise. */
    suspend fun zoomToGuidedRegion() {
        val grid = guidedGrid(container)
        val target = guidedTarget(guided.region, grid, rtl, container)
        if (reduceMotion) zoom.snapTo(target.scale, target.offsetX, target.offsetY)
        else zoom.animateTo(target.scale, target.offsetX, target.offsetY)
    }

    // A new page or a new mode starts fully zoomed out, unless guided stepping owns the camera.
    LaunchedEffect(cursor, paged) { if (!guidedStepping) zoom.reset() }

    // Guided stepping shows the whole page again when the page changes, unless the step itself turned it.
    LaunchedEffect(cursor) { if (guided.armed) guided.armed = false else guided.reset() }

    // Each guided step moves the camera to its region; stepping back out zooms back to the whole page.
    LaunchedEffect(guided.region, guidedStepping) {
        if (!guidedStepping) return@LaunchedEffect
        if (guided.isWholePage) zoom.reset() else zoomToGuidedRegion()
    }

    // Zen mode hides the bars the moment it starts, and says how to get out.
    LaunchedEffect(zen) {
        if (zen) {
            barsVisible = false
            viewModel.toast("Zen mode: volume keys turn pages, long-press to exit")
        }
    }

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
        // Keep the paired watch's at-a-glance view in step with the reader.
        page.seriesTitle?.let { title ->
            WearBridge.publishProgress(title, segment.chapter.number, at.page, segment.pages.size)
        }
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

    /** A light buzz on page turns, when the setting and haptics are both on. */
    fun pageTurnHaptic() {
        if (settings.hapticPageTurn && settings.haptics) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    // Tap navigation in paged mode slides, fades, or jumps, as set. Reduce motion makes every turn instant.
    suspend fun PagerState.turnTo(index: Int) {
        val target = index.coerceIn(0, pagerCount - 1)
        if (target != currentPage) pageTurnHaptic()
        if (settings.pageTransition == PageTransition.Slide && !reduceMotion) animateScrollToPage(target) else scrollToPage(target)
    }

    /**
     * One guided step. Within the page the camera moves to the next region; past the last region it
     * turns the page and opens on the first region, and symmetrically backwards. The step arms the
     * guided state first, so the page-change reset does not wipe the region the new page opens on.
     */
    suspend fun guidedTurn(direction: Int) {
        val grid = guidedGrid(container)
        val count = grid * grid
        if (direction > 0) {
            if (guided.region + 1 < count) {
                guided.show(guided.region + 1)
                pageTurnHaptic()
            } else {
                val target = pagerState.currentPage + 1
                if (target < pagerCount - 1) {
                    guided.armed = true
                    pagerState.turnTo(target)
                    guided.show(0)
                } else {
                    // The end-of-chapter card gets a plain page turn, with no guided camera.
                    pagerState.turnTo(target)
                }
            }
        } else {
            when {
                guided.region > 0 -> {
                    guided.show(guided.region - 1)
                    pageTurnHaptic()
                }
                guided.region == 0 -> guided.show(-1)
                else -> {
                    val target = (pagerState.currentPage - 1).coerceAtLeast(0)
                    if (target != pagerState.currentPage) {
                        guided.armed = true
                        pagerState.turnTo(target)
                        guided.show(count - 1)
                    }
                }
            }
        }
    }

    /** One page forward or back in paged mode: a guided step when stepping is on, a page turn otherwise. */
    suspend fun turnPage(direction: Int, onContentPage: Boolean) {
        if (guidedStepping && onContentPage) guidedTurn(direction) else pagerState.turnTo(pagerState.currentPage + direction)
    }

    /** Scrolls the strip by most of a screen: smoothly, or instantly with reduce motion. */
    suspend fun scrollStripBy(direction: Int) {
        val amount = pageScrollAmount(listState.layoutInfo.viewportSize.height, direction)
        if (reduceMotion) listState.scrollBy(amount) else listState.animateScrollBy(amount)
        pageTurnHaptic()
    }

    // Volume keys turn a page in paged mode and scroll most of a screen in the vertical strip.
    // Zen mode reaches this with the setting off, since it routes all navigation to the volume keys.
    LaunchedEffect(paged, listState, pagerState) {
        VolumeKeyPager.events.collect { direction ->
            if (paged) {
                turnPage(direction, onContentPage = pagerState.currentPage < pagerCount - 1)
            } else {
                scrollStripBy(direction)
            }
        }
    }

    // Watch remote: a page turn from the paired watch behaves like a volume-key press.
    LaunchedEffect(paged, listState, pagerState) {
        WearBridge.turns.collect { turn ->
            if (turn == WearBridge.PageTurn.Next) {
                if (paged) turnPage(1, onContentPage = pagerState.currentPage < pagerCount - 1) else scrollStripBy(1)
            } else {
                if (paged) turnPage(-1, onContentPage = true) else scrollStripBy(-1)
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
        if (paged) pagerState.scrollToPage(pagerIndexOfPage(target)) else listState.scrollToItem(stripIndexOf(strip, cursor.segment, target))
        cursor = Cursor(cursor.segment, target)
    }

    // Text-to-speech narration: reads page announcements aloud through a foreground service, so it
    // keeps going with the screen off and headset buttons control it. Stops when the reader closes.
    var narrationRunning by remember { mutableStateOf(ReaderTtsService.running) }
    fun toggleNarration() {
        if (ReaderTtsService.running) {
            ReaderTtsService.toggle(context)
        } else {
            val total = current.pages.size
            val texts = List(total) { i -> "Episode ${current.chapter.number}, page ${i + 1} of $total" }
            ReaderTtsService.start(
                context,
                page.seriesTitle ?: "Dexter",
                texts,
                IntArray(total) { it },
                a11y.ttsAutoAdvance,
            )
        }
        narrationRunning = ReaderTtsService.running
    }
    DisposableEffect(Unit) {
        onDispose { if (ReaderTtsService.running) ReaderTtsService.stop(context) }
    }
    // The service broadcasts each narrated page; with auto-advance on, the reader follows along.
    val ttsAutoAdvance by rememberUpdatedState(a11y.ttsAutoAdvance)
    LaunchedEffect(Unit) {
        TtsPageEvents.pages.collect { index -> if (ttsAutoAdvance) goToPage(index) }
    }

    // A Bluetooth clicker or gamepad turns pages while the reader is open, when the setting is on.
    DisposableEffect(power.gamepadReader) {
        GamepadKeys.active = power.gamepadReader
        onDispose { GamepadKeys.active = false }
    }
    LaunchedEffect(power.gamepadReader, paged) {
        if (!power.gamepadReader) return@LaunchedEffect
        GamepadKeys.events.collect { direction ->
            if (paged) turnPage(direction, onContentPage = pagerState.currentPage < pagerCount - 1)
            else scrollStripBy(direction)
        }
    }

    // Voice control: "next page", "go back", "scroll down" and friends, when the setting is on.
    val voiceRecognizer = remember { VoiceRecognizer(context) }
    DisposableEffect(voiceRecognizer) { onDispose { voiceRecognizer.destroy() } }
    LaunchedEffect(a11y.voiceControl, paged) {
        if (!a11y.voiceControl) return@LaunchedEffect
        VoiceCommands.events.collect { command ->
            when (command) {
                VoiceCommand.NextPage -> if (paged) turnPage(1, onContentPage = pagerState.currentPage < pagerCount - 1) else scrollStripBy(1)
                VoiceCommand.PreviousPage -> if (paged) turnPage(-1, onContentPage = true) else scrollStripBy(-1)
                VoiceCommand.ScrollDown -> scrollStripBy(1)
                VoiceCommand.ScrollUp -> scrollStripBy(-1)
            }
        }
    }

    /** The toolbar's extra actions: the thumbnail grid, the sleep timer, the binge toggle, narration. */
    fun onToolbarAction(action: ToolbarAction) {
        when (action) {
            ToolbarAction.Thumbnails -> showThumbnails = true
            ToolbarAction.SleepTimer -> showSleepDialog = true
            ToolbarAction.Cast -> showCastPicker = true
            ToolbarAction.Narration -> toggleNarration()
            ToolbarAction.Binge -> {
                val on = !readerUi.bingeMode
                scope.launch { uiPrefs.setBingeMode(on) }
                viewModel.toast(if (on) "Binge mode on" else "Binge mode off")
            }
            else -> Unit
        }
    }

    /** The page under [y] in the strip, or the one on screen in paged mode. */
    fun pageAt(y: Float): Cursor {
        if (paged || zoom.isZoomed) return cursor
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { y.toInt() in it.offset until it.offset + it.size } ?: return cursor
        return (strip.getOrNull(item.index) as? StripItem.Page)?.let { Cursor(it.segment, it.page) } ?: cursor
    }

    // Smart background: tint the reader background toward the colour of the page on screen.
    val smartKey: String? = if (settings.smartBackground && !eInk) {
        if (paged) "spread:${pagerState.currentPage}" else strip.getOrNull(listState.firstVisibleItemIndex)?.key
    } else {
        null
    }
    val pageTint = smartKey?.let { smartColors[it] }
    val stripBgColor = stripBackgroundColor(stripBgChoice, settings)
    val background = stripBgColor
        ?: pageTint?.let { blendColors(readerBackgroundColor(settings.readerBackground), it, 0.55f) }
        ?: readerBackgroundColor(settings.readerBackground)
    // Pages report their mean colour as they load, for the tint above.
    val reportColor: ((String, Color) -> Unit)? = if (settings.smartBackground && !eInk) {
        { key, color -> smartColors[key] = color }
    } else {
        null
    }

    // The predictive-back animation shrinks this whole box away on Android 14+ instead of popping.
    PredictiveBack(enabled = readerUi.predictiveBack, onBack = onBack) {
        Box(
            Modifier
                .fillMaxSize()
                .background(background)
                .onSizeChanged { container = it }
                .pointerInput(paged, rtl, settings, zen) {
                    detectTapGestures(
                        // Zen mode shields taps: they do nothing, and a long press is the way out.
                        onTap = if (zen) {
                            null
                        } else {
                            { offset ->
                                if (!paged) {
                                    if (settings.tapZonesInWebtoon || settings.tapToScroll) {
                                        when (webtoonTapAction(offset.y, size.height.toFloat(), settings.oneHandedMode)) {
                                            TapAction.Previous -> scope.launch { scrollStripBy(-1) }
                                            TapAction.Next -> scope.launch { scrollStripBy(1) }
                                            TapAction.ToggleBars -> barsVisible = !barsVisible
                                        }
                                    } else {
                                        barsVisible = !barsVisible
                                    }
                                } else {
                                    // Guided stepping stays off the end-of-chapter card, where taps turn pages as usual.
                                    val contentPage = pagerState.currentPage < pagerCount - 1
                                    when (
                                        tapZoneAction(
                                            offset,
                                            size,
                                            settings.tapZoneLayout,
                                            rtl,
                                            invert = settings.invertTapZones,
                                            oneHanded = settings.oneHandedMode,
                                        )
                                    ) {
                                        TapAction.ToggleBars -> barsVisible = !barsVisible
                                        TapAction.Next -> scope.launch { turnPage(1, contentPage) }
                                        TapAction.Previous -> scope.launch { turnPage(-1, contentPage) }
                                    }
                                }
                            }
                        },
                        onDoubleTap = if (zen) {
                            null
                        } else {
                            { offset ->
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                zoom.toggle(offset, container)
                                guided.reset()
                            }
                        },
                        onLongPress = { offset ->
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            if (zen) viewModel.setZen(false) else menuFor = pageAt(offset.y)
                        },
                    )
                }
                .zoomGestures(zoom) { container }
                // The S-Pen barrel button turns pages: primary forward, secondary back.
                .stylusPenButton(
                    enabled = readerUi.stylusPenButton,
                    onNext = { scope.launch { if (paged) turnPage(1, pagerState.currentPage < pagerCount - 1) else scrollStripBy(1) } },
                    onPrevious = { scope.launch { if (paged) turnPage(-1, pagerState.currentPage < pagerCount - 1) else scrollStripBy(-1) } },
                )
                // Taps and swipes turn pages by sight. TalkBack gets the same moves as actions.
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Next page") {
                            scope.launch {
                                if (paged) turnPage(1, pagerState.currentPage < pagerCount - 1) else scrollStripBy(1)
                            }
                            true
                        },
                        CustomAccessibilityAction("Previous page") {
                            scope.launch {
                                if (paged) turnPage(-1, pagerState.currentPage < pagerCount - 1) else scrollStripBy(-1)
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
                        // Reduce motion turns the fade into an instant cut.
                        val fade = settings.pageTransition == PageTransition.Fade && !reduceMotion
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
                                val shown = if (spreads) pairs.getOrNull(index).orEmpty() else listOf(index)
                                PagedPage(
                                    pagedChapter,
                                    shown,
                                    rtl,
                                    settings,
                                    ::filterFor,
                                    viewModel::renewPages,
                                    sampleKey = "spread:$index",
                                    onSampled = reportColor,
                                    onColorSampled = reportPageColor,
                                    onAspectSampled = reportAspect,
                                )
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
                        verticalArrangement = Arrangement.spacedBy(stripGap.dp),
                    ) {
                        // Keyed by chapter and page, so new page addresses and joined chapters keep your place.
                        items(strip, key = { it.key }, contentType = { it::class }) { item ->
                            when (item) {
                                is StripItem.Page -> {
                                    val segment = segments[item.segment]
                                    val url = segment.pages[item.page]
                                    PageImage(
                                        url = url,
                                        index = item.page,
                                        layout = PageLayout.Strip,
                                        crop = settings.cropBorders,
                                        segment = splits[item.segment to item.page]?.getOrNull(item.part),
                                        colorFilter = filterFor(item.key),
                                        onGaveUp = { viewModel.renewPages(segment.chapter.id) },
                                        colorKey = item.key,
                                        onSampled = reportColor,
                                        onColorSampled = reportPageColor,
                                        onHeightSampled = if (a11y.tallPageSplit) { _, height -> pageHeights[url] = height } else null,
                                        corner = readerUi.stripCornerDp.dp,
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

            // A patterned strip background draws behind the pages; plain colors replaced the background above.
            StripPattern(stripBgChoice, Modifier.fillMaxSize())

            // A hovering stylus gets a 2x magnifier under its tip.
            val peekUrl = if (paged) pagedChapter.pages.getOrNull(position) else current.pages.getOrNull(position)
            StylusHoverPeek(enabled = readerUi.stylusHoverPeek, pageUrl = peekUrl, containerSize = container)

            // Dimming sits over the pages and under the bars. It does not take touches. E-ink mode skips it.
            if (settings.readerDim > 0 && !eInk) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = settings.readerDim.coerceIn(0, 70) / 100f)))
            }

            // Zen mode adds its own hard dim over everything but the bars, which are already hidden.
            if (zen) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            }

            // The sleep timer stops auto-scroll and narration, and dims the screen, when it fires. A tap clears the dim.
            var sleepDimmed by remember { mutableStateOf(false) }
            SleepTimer(minutes = readerUi.sleepTimerMinutes) {
                viewModel.updateSettings { it.copy(autoScrollLevel = 0) }
                ReaderTtsService.stop(context)
                sleepDimmed = true
                viewModel.toast("Sleep timer: auto-scroll stopped, screen dimmed")
            }
            if (sleepDimmed) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { sleepDimmed = false },
                )
            }

            // Zen mode hides every last bit of chrome, counter included.
            if (!barsVisible && !zen) {
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
                val bookmarked = bookmarks.any { it.chapterId == current.chapter.id && it.page == position }
                // One-handed mode drops the top bar: its actions move into the bottom bar, near the thumb.
                if (!settings.oneHandedMode) {
                    ReaderTopBar(
                        segment = current,
                        seriesTitle = page.seriesTitle,
                        bookmarked = bookmarked,
                        incognito = settings.incognito,
                        castManager = castManager,
                        onBookmark = {
                            barTouch++
                            viewModel.toggleBookmark(current.chapter, position)
                        },
                        onBack = onBack,
                        onOpenOptions = onOpenOptions,
                        actions = topActions,
                        onToolbarAction = ::onToolbarAction,
                        showNarration = a11y.ttsEnabled,
                        narrationRunning = narrationRunning,
                        voiceButton = if (a11y.voiceControl) {
                            { VoiceControlButton(voiceRecognizer) }
                        } else {
                            null
                        },
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
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
                    oneHanded = settings.oneHandedMode,
                    onBack = onBack,
                    bookmarked = bookmarked,
                    onBookmark = {
                        barTouch++
                        viewModel.toggleBookmark(current.chapter, position)
                    },
                    onOpenOptions = onOpenOptions,
                    actions = bottomActions,
                    topActions = topActions,
                    pageUrls = current.pages,
                    scrubberPreview = readerUi.scrubberPreview,
                    onPageCounter = { if (readerUi.thumbnailsEnabled) showThumbnails = true else jumpTo = (position + 1).toString() },
                    onToolbarAction = ::onToolbarAction,
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
                        onOcr = { menuFor = null; ocrUrl = url },
                        onDismiss = { menuFor = null },
                    )
                }
            }

            // On-demand OCR: recognize the page's text and let the reader tap blocks to copy, share, or translate.
            ocrUrl?.let { OcrLookupSheet(imageUrl = it, onDismiss = { ocrUrl = null }) }

            // Binge mode: at the end of a chapter, count down and open the next one automatically.
            var bingeDismissed by remember { mutableStateOf<String?>(null) }
            val bingeNext = page.chapters.firstOrNull { it.id == current.nextId }
            val atChapterEnd = if (paged) {
                pagerState.currentPage >= pagerCount - 1
            } else {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                lastVisible != null && strip.isNotEmpty() && lastVisible.index >= strip.lastIndex
            }
            // In the continuous strip the next chapter joins on its own, so there is nothing to binge to.
            val bingeContinuing = !paged && continuous && current.nextId != null && segments.size < MAX_SEGMENTS
            if (readerUi.bingeMode && atChapterEnd && current.nextId != null && !bingeContinuing && bingeDismissed != current.chapter.id) {
                BingeCountdown(
                    seconds = readerUi.bingeSeconds,
                    nextLabel = bingeNext?.let { "Ep. ${it.number}" } ?: "the next episode",
                    onAdvance = { current.nextId?.let(onOpenChapter) },
                    onCancel = { bingeDismissed = current.chapter.id },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 128.dp),
                )
            }

            if (showThumbnails) {
                ChapterThumbnailSheet(
                    pages = current.pages,
                    position = position,
                    onSelect = { index ->
                        showThumbnails = false
                        scope.launch { goToPage(index) }
                    },
                    onGoToPage = {
                        showThumbnails = false
                        jumpTo = (position + 1).toString()
                    },
                    onDismiss = { showThumbnails = false },
                )
            }

            if (showCastPicker) CastPicker(castManager, onDismiss = { showCastPicker = false })
            if (showSleepDialog) {
                SleepTimerDialog(
                    currentMinutes = readerUi.sleepTimerMinutes,
                    onSelect = { minutes -> scope.launch { uiPrefs.setSleepTimerMinutes(minutes) } },
                    onDismiss = { showSleepDialog = false },
                )
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
    filterFor: (String?) -> ColorFilter?,
    onGaveUp: (String) -> Unit,
    sampleKey: String? = null,
    onSampled: ((String, Color) -> Unit)? = null,
    onColorSampled: ((String, Boolean) -> Unit)? = null,
    onAspectSampled: ((String, Float) -> Unit)? = null,
) {
    val renew = { onGaveUp(segment.chapter.id) }
    if (shown.size > 1) {
        // A spread reads in the chapter's direction, so right to left puts the earlier page on the right.
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            (if (rtl) shown.reversed() else shown).forEach { index ->
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    PageImage(
                        segment.pages[index], index, PageLayout.Screen, settings.cropBorders, filterFor(sampleKey), renew,
                        sampleKey, onSampled, onColorSampled, onAspectSampled,
                    )
                }
            }
        }
        return
    }
    val index = shown.firstOrNull() ?: return
    val url = segment.pages[index]
    when (settings.pageFit) {
        PageFit.Screen -> PageImage(
            url, index, PageLayout.Screen, settings.cropBorders, filterFor(sampleKey), renew,
            sampleKey, onSampled, onColorSampled, onAspectSampled,
        )
        PageFit.Width -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            PageImage(
                url, index, PageLayout.Strip, settings.cropBorders, filterFor(sampleKey), renew,
                sampleKey, onSampled, onColorSampled, onAspectSampled,
            )
        }
        PageFit.Height -> Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            PageImage(
                url, index, PageLayout.Height, settings.cropBorders, filterFor(sampleKey), renew,
                sampleKey, onSampled, onColorSampled, onAspectSampled,
            )
        }
    }
}

/**
 * One page image. A failed load retries twice on its own, then asks for new page addresses through
 * [onGaveUp] and shows a box that retries when tapped. In the strip the image fills the width at its
 * natural height. Fitted to the screen, it shows whole. At the screen's height, it may run off the sides.
 */
@Composable
private fun PageImage(
    url: String,
    index: Int,
    layout: PageLayout,
    crop: Boolean,
    colorFilter: ColorFilter?,
    onGaveUp: () -> Unit,
    colorKey: String? = null,
    onSampled: ((String, Color) -> Unit)? = null,
    /** Reports whether the loaded page is color, for the night-filter exemption. */
    onColorSampled: ((String, Boolean) -> Unit)? = null,
    /** Reports the loaded page's width-to-height ratio, for spread detection. */
    onAspectSampled: ((String, Float) -> Unit)? = null,
    /** Reports the loaded page's pixel height, for tall-page splitting. */
    onHeightSampled: ((String, Int) -> Unit)? = null,
    /** When set, only this chunk of a split tall page is decoded. */
    segment: PageSegment? = null,
    /** Rounded page corners in the strip; 0 is square. */
    corner: Dp = 0.dp,
) {
    val context = LocalPlatformContext.current
    // Bumping the attempt count rebuilds the image, which asks the server again.
    var attempt by remember { mutableIntStateOf(0) }
    // Two quiet retries with a growing pause before the tap-to-retry message shows. New addresses start over.
    var autoRetries by remember(url) { mutableIntStateOf(0) }
    var failed by remember(url) { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val request = remember(url, crop, segment) { pageRequest(context, url, crop, segment) }
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
            .then(if (corner > 0.dp && layout == PageLayout.Strip) Modifier.clip(RoundedCornerShape(corner)) else Modifier)
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
                onSuccess = { loadedImage ->
                    loaded = true
                    failed = false
                    val bitmap = (loadedImage.result.image as? BitmapImage)?.bitmap
                    if (bitmap != null) {
                        // Smart background: report this page's mean colour once its bitmap is in hand.
                        val key = colorKey
                        val report = onSampled
                        if (key != null && report != null) {
                            // A sampling failure costs the tint, never the app.
                            scope.launch(Dispatchers.Default) { runCatching { pageSample(context, url)?.let { report(key, averageColor(it)) } } }
                        }
                        // Color pages are exempt from the night filters; wide pages feed spread detection.
                        val colorKey2 = colorKey
                        val colorReport = onColorSampled
                        if (colorKey2 != null && colorReport != null) {
                            scope.launch(Dispatchers.Default) { runCatching { pageSample(context, url)?.let { colorReport(colorKey2, isColorful(it)) } } }
                        }
                        val aspectReport = onAspectSampled
                        if (aspectReport != null) {
                            aspectReport(url, bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1))
                        }
                        val heightReport = onHeightSampled
                        if (heightReport != null) {
                            heightReport(url, bitmap.height)
                        }
                    }
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
    onOcr: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Page $pageNumber", style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        listOf(
            "Save to Pictures" to onSave,
            "Share" to onShare,
            "Copy" to onCopy,
            "Recognize text" to onOcr,
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
