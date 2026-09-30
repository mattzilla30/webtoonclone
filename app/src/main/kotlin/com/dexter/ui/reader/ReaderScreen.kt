package com.dexter.ui.reader

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.SingletonImageLoader
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import com.dexter.R
import com.dexter.data.Chapter
import com.dexter.data.ReaderBackground
import com.dexter.data.ReadingMode
import com.dexter.data.Settings
import com.dexter.data.TapAction
import com.dexter.data.tapAction
import com.dexter.ui.ChoiceChip
import com.dexter.ui.LoadView
import com.dexter.ui.SyncedSlider
import com.dexter.ui.iconTap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val Bar = Color(0xE6181818)

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

/** On a tablet a vertical strip this wide reads better than one stretched across the screen. */
private val MAX_STRIP_WIDTH = 720.dp

/** Pixels scrolled every 16 ms at each auto-scroll level. Level 0 is off. */
private val AUTO_SCROLL_PX = floatArrayOf(0f, 1.5f, 3f, 5f, 8f, 12f)

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    seriesId: String,
    onOpenChapter: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val chosenMode by viewModel.chosenMode.collectAsState()
    var showOptions by remember { mutableStateOf(false) }
    val zoom = remember { ZoomState() }

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
                onChange = viewModel::updateSettings,
                onMode = viewModel::setMode,
                onDismiss = { showOptions = false },
            )
        }
    }
}

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
    val scope = rememberCoroutineScope()
    var barsVisible by remember { mutableStateOf(true) }
    var showChapters by remember { mutableStateOf(false) }
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

    LaunchedEffect(Unit) {
        snapshotFlow { position }.distinctUntilChanged().collect { viewModel.saveProgress(it) }
    }

    // Fetch the next few pages ahead of the reader so they are ready when you arrive.
    LaunchedEffect(page.pages) {
        val loader = SingletonImageLoader.get(context)
        snapshotFlow { position }.distinctUntilChanged().collect { first ->
            for (next in first + 1..first + PRELOAD_AHEAD) {
                page.pages.getOrNull(next)?.let { url ->
                    loader.enqueue(ImageRequest.Builder(context).data(url).build())
                }
            }
        }
    }

    // Near the end of the chapter, preload the first pages of the next one.
    LaunchedEffect(page.pages, settings.prefetchPages) {
        if (settings.prefetchPages <= 0) return@LaunchedEffect
        snapshotFlow { position >= count - NEXT_CHAPTER_PRELOAD_AT }.first { it }
        val loader = SingletonImageLoader.get(context)
        viewModel.nextChapterPreview(settings.prefetchPages).forEach { url ->
            loader.enqueue(ImageRequest.Builder(context).data(url).build())
        }
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
            while (isActive) {
                if (!listState.canScrollForward) {
                    viewModel.updateSettings { it.copy(autoScrollLevel = 0) }
                    break
                }
                listState.scrollBy(AUTO_SCROLL_PX[level])
                delay(16)
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
                    onDoubleTap = { offset -> zoom.toggle(offset, container) },
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
                        PageImage(page.pages[index], index, fill = true)
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
                    itemsIndexed(page.pages, key = { _, url -> url }) { index, url -> PageImage(url, index, fill = false) }
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
            Text(
                "${position + 1} / $count",
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .background(Bar, RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }

        if (barsVisible) {
            Row(
                Modifier.fillMaxWidth().background(Bar).padding(horizontal = 16.dp, vertical = 12.dp).align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White, modifier = Modifier.iconTap(onBack))
                Text("Ep. ${page.chapter.number}", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 16.dp))
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.reader_options),
                    tint = Color.White,
                    modifier = Modifier.padding(end = 16.dp).clickable(onClick = onOpenOptions),
                )
                Icon(
                    Icons.Default.Share,
                    contentDescription = stringResource(R.string.share),
                    tint = Color.White,
                    modifier = Modifier.clickable {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "https://mangadex.org/chapter/${page.chapter.id}")
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    },
                )
            }
            Column(Modifier.fillMaxWidth().background(Bar).align(Alignment.BottomCenter)) {
                if (count > 1) {
                    // Right-to-left reading puts the first page on the right, so the slider runs that way too.
                    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LocalLayoutDirection.current) {
                        SyncedSlider(
                            value = position.toFloat(),
                            onValueChange = { scope.launch { goToPage(it.roundToInt()) } },
                            valueRange = 0f..lastIndex.toFloat(),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${position + 1} / $count", color = Color.White, fontSize = 12.sp)
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = stringResource(R.string.chapters),
                        tint = Color.White,
                        modifier = Modifier.clickable { showChapters = true },
                    )
                    Text("#${page.index + 1}", color = Color.White, fontSize = 12.sp)
                    Row {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.previous_episode),
                            tint = if (page.prevId != null) Color.White else Color.DarkGray,
                            modifier = Modifier.clickable(enabled = page.prevId != null) { onOpenChapter(page.prevId!!) },
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.next_episode),
                            tint = if (page.nextId != null) Color.White else Color.DarkGray,
                            modifier = Modifier.padding(start = 16.dp).clickable(enabled = page.nextId != null) { onOpenChapter(page.nextId!!) },
                        )
                    }
                }
            }
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
 * One page image. A failed load shows a box that retries when tapped. In the vertical strip the image
 * fills the width at its natural height. In paged mode it is fitted whole into the screen.
 */
@Composable
private fun PageImage(url: String, index: Int, fill: Boolean) {
    // Bumping the attempt count rebuilds the image, which asks the server again.
    var attempt by remember { mutableIntStateOf(0) }
    // Two quiet retries with a growing pause before the tap-to-retry message shows.
    var autoRetries by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    key(attempt) {
        SubcomposeAsyncImage(
            model = url,
            onError = {
                if (autoRetries < AUTO_RETRIES) {
                    val wait = RETRY_BASE_MS shl autoRetries
                    autoRetries++
                    scope.launch {
                        delay(wait)
                        attempt++
                    }
                }
            },
            contentDescription = "Page ${index + 1}",
            contentScale = if (fill) ContentScale.Fit else ContentScale.FillWidth,
            modifier = if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            loading = {
                Box(Modifier.then(if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(500.dp)).background(Color(0xFF2A2A2A)))
            },
            error = {
                Box(
                    Modifier
                        .then(if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(200.dp))
                        .background(Color(0xFF2A2A2A))
                        .clickable {
                            autoRetries = 0
                            attempt++
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (autoRetries < AUTO_RETRIES) "Retrying page ${index + 1}..." else "Page ${index + 1} failed to load. Tap to retry.",
                        color = Color.White,
                    )
                }
            },
            success = { SubcomposeAsyncImageContent() },
        )
    }
}

@Composable
private fun EndOfChapter(page: ReaderPage, textColor: Color, onOpenChapter: (String) -> Unit, modifier: Modifier) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("End of Ep. ${page.chapter.number}", color = textColor, fontWeight = FontWeight.Bold)
        if (page.nextId != null) {
            Text(
                "Next episode",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.primary)
                    .clickable { onOpenChapter(page.nextId) }.padding(horizontal = 28.dp, vertical = 12.dp),
            )
        }
    }
}

/** Every readable chapter, oldest first, opened at the current one. Tapping one jumps to it. */
@Composable
private fun ChapterPicker(chapters: List<Chapter>, currentId: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = chapters.indexOfFirst { it.id == currentId }.coerceAtLeast(0))
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(vertical = 12.dp)) {
            Text(stringResource(R.string.chapters), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            LazyColumn(Modifier.heightIn(max = 420.dp), state = listState) {
                itemsIndexed(chapters, key = { _, c -> c.id }) { _, chapter ->
                    val current = chapter.id == currentId
                    Text(
                        buildString {
                            append("Ep. ${chapter.number}")
                            if (chapter.title.isNotBlank()) append(" · ${chapter.title}")
                        },
                        fontSize = 14.sp,
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(chapter.id) }.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

private val modeLabels = listOf(
    ReadingMode.Auto to "Auto",
    ReadingMode.Vertical to "Vertical strip",
    ReadingMode.PagedLtr to "Pages, left to right",
    ReadingMode.PagedRtl to "Pages, right to left",
)

/** Reading mode, dimming, background, auto-scroll speed, and volume-key paging, saved as you change them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderOptions(
    settings: Settings,
    mode: ReadingMode,
    chosenMode: ReadingMode,
    onChange: ((Settings) -> Settings) -> Unit,
    onMode: (ReadingMode) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(20.dp)) {
            Text(stringResource(R.string.reader_options), fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Text(stringResource(R.string.reading_mode_for_this_series), fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                modeLabels.forEach { (value, label) -> ChoiceChip(label, chosenMode == value) { onMode(value) } }
            }

            Text(stringResource(R.string.dimming), fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
            SyncedSlider(
                value = settings.readerDim.toFloat(),
                onValueChange = { value -> onChange { it.copy(readerDim = value.roundToInt()) } },
                valueRange = 0f..70f,
            )

            Text(stringResource(R.string.background), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderBackground.entries.forEach { choice ->
                    ChoiceChip(choice.name, settings.readerBackground == choice) { onChange { it.copy(readerBackground = choice) } }
                }
            }

            if (mode == ReadingMode.Vertical) {
                Text(stringResource(R.string.auto_scroll), fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..5).forEach { speed ->
                        ChoiceChip(if (speed == 0) "Off" else speed.toString(), settings.autoScrollLevel == speed) {
                            onChange { it.copy(autoScrollLevel = speed) }
                        }
                    }
                }
            }

            if (mode == ReadingMode.Vertical) {
                Text(stringResource(R.string.space_between_pages), fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "None", 8 to "Small", 24 to "Large").forEach { (gap, label) ->
                        ChoiceChip(label, settings.pageGap == gap) { onChange { it.copy(pageGap = gap) } }
                    }
                }
            }

            Text(stringResource(R.string.load_next_chapter_ahead), fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Off", 3 to "First pages", 100 to "Whole chapter").forEach { (count, label) ->
                    ChoiceChip(label, settings.prefetchPages == count) { onChange { it.copy(prefetchPages = count) } }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.keep_screen_on), fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = settings.keepScreenOn, onCheckedChange = { on -> onChange { it.copy(keepScreenOn = on) } })
            }

            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (mode == ReadingMode.Vertical) "Volume keys scroll" else "Volume keys turn pages", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = settings.volumeKeys, onCheckedChange = { on -> onChange { it.copy(volumeKeys = on) } })
            }

            Text(
                "Done",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.End).clickable(onClick = onDismiss).padding(top = 16.dp, start = 16.dp),
            )
        }
    }
}
