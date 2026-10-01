package com.dexter.ui.reader

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
import com.dexter.data.ReaderOrientation
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

/** The translucent panel colour behind the reader's bars. */
@Composable
private fun barColor() = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)

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
    val hasSeriesLook by viewModel.hasSeriesLook.collectAsState()
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
            Surface(
                shape = CircleShape,
                color = barColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            ) {
                Text("${position + 1} / $count", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }

        val barIcons = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface, disabledContentColor = Color.DarkGray)
        if (barsVisible) {
            Surface(
                color = barColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.extraLarge.copy(topStart = CornerSize(0.dp), topEnd = CornerSize(0.dp)),
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, colors = barIcons) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text("Ep. ${page.chapter.number}", style = MaterialTheme.typography.titleMediumEmphasized)
                        page.seriesTitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    IconButton(onClick = onOpenOptions, colors = barIcons) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.reader_options))
                    }
                    IconButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, (page.seriesTitle?.let { "$it, Ep. ${page.chapter.number}\n" }.orEmpty()) + "https://mangadex.org/chapter/${page.chapter.id}")
                            }
                            context.startActivity(Intent.createChooser(send, null))
                        },
                        colors = barIcons,
                    ) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                    }
                }
            }
            Surface(
                color = barColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            ) {
                Column {
                    if (count > 1) {
                        // Right-to-left reading puts the first page on the right, so the slider runs that way too.
                        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LocalLayoutDirection.current) {
                            SyncedSlider(
                                value = position.toFloat(),
                                onValueChange = { scope.launch { goToPage(it.roundToInt()) } },
                                valueRange = 0f..lastIndex.toFloat(),
                                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(onClick = { jumpTo = (position + 1).toString() }) { Text("${position + 1} / $count", style = MaterialTheme.typography.labelLarge) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showChapters = true }, colors = barIcons) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.chapters))
                            }
                            IconButton(onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onOpenChapter(page.prevId!!)
                            }, enabled = page.prevId != null, colors = barIcons) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_episode))
                            }
                            IconButton(onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onOpenChapter(page.nextId!!)
                            }, enabled = page.nextId != null, colors = barIcons) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_episode))
                            }
                        }
                    }
                }
            }
        }

        jumpTo?.let { typed ->
            val target = typed.toIntOrNull()
            AlertDialog(
                onDismissRequest = { jumpTo = null },
                title = { Text("Go to page") },
                text = {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { jumpTo = it.filter(Char::isDigit).take(4) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { Text("1 to $count") },
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = target != null && target in 1..count,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            scope.launch { goToPage((target ?: 1) - 1) }
                            jumpTo = null
                        },
                    ) { Text("Go") }
                },
                dismissButton = { TextButton(onClick = { jumpTo = null }) { Text("Cancel") } },
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
                Box(Modifier.then(if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(500.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh))
            },
            error = {
                Box(
                    Modifier
                        .then(if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(200.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable {
                            autoRetries = 0
                            attempt++
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (autoRetries < AUTO_RETRIES) "Retrying page ${index + 1}..." else "Page ${index + 1} failed to load. Tap to retry.",
                        color = MaterialTheme.colorScheme.onSurface,
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
        Text("End of Ep. ${page.chapter.number}", color = textColor, style = MaterialTheme.typography.titleMediumEmphasized)
        if (page.nextId != null) {
            Button(
                onClick = { onOpenChapter(page.nextId) },
                modifier = Modifier.padding(top = 16.dp).heightIn(min = ButtonDefaults.MediumContainerHeight),
            ) { Text("Next episode") }
        }
    }
}

/** Every readable chapter, oldest first, opened at the current one. Tapping one jumps to it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterPicker(chapters: List<Chapter>, currentId: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = chapters.indexOfFirst { it.id == currentId }.coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.chapters), style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(Modifier.heightIn(max = 480.dp), state = listState) {
            itemsIndexed(chapters, key = { _, c -> c.id }) { _, chapter ->
                val current = chapter.id == currentId
                Surface(
                    onClick = { onSelect(chapter.id) },
                    color = if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    contentColor = if (current) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            buildString {
                                append("Ep. ${chapter.number}")
                                if (chapter.title.isNotBlank()) append(" · ${chapter.title}")
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        chapter.group?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
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
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ReaderOptions(
    settings: Settings,
    mode: ReadingMode,
    chosenMode: ReadingMode,
    seriesLook: Boolean,
    onSeriesLook: (Boolean) -> Unit,
    onChange: ((Settings) -> Settings) -> Unit,
    onMode: (ReadingMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(stringResource(R.string.reader_options), style = MaterialTheme.typography.titleLargeEmphasized)

            Text(stringResource(R.string.reading_mode_for_this_series), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                modeLabels.forEach { (value, label) -> ChoiceChip(label, chosenMode == value) { onMode(value) } }
            }

            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Separate look for this series", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = seriesLook, onCheckedChange = onSeriesLook)
            }

            Text(stringResource(R.string.dimming), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
            SyncedSlider(
                value = settings.readerDim.toFloat(),
                onValueChange = { value -> onChange { it.copy(readerDim = value.roundToInt()) } },
                valueRange = 0f..70f,
            )

            Text(stringResource(R.string.background), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderBackground.entries.forEach { choice ->
                    ChoiceChip(choice.name, settings.readerBackground == choice) { onChange { it.copy(readerBackground = choice) } }
                }
            }

            if (mode == ReadingMode.Vertical) {
                Text(stringResource(R.string.auto_scroll), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..5).forEach { speed ->
                        ChoiceChip(if (speed == 0) "Off" else speed.toString(), settings.autoScrollLevel == speed) {
                            onChange { it.copy(autoScrollLevel = speed) }
                        }
                    }
                }
            }

            if (mode == ReadingMode.Vertical) {
                Text(stringResource(R.string.space_between_pages), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "None", 8 to "Small", 24 to "Large").forEach { (gap, label) ->
                        ChoiceChip(label, settings.pageGap == gap) { onChange { it.copy(pageGap = gap) } }
                    }
                }
            }

            Text("Screen direction", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderOrientation.entries.forEach { choice ->
                    ChoiceChip(choice.name, settings.readerOrientation == choice) { onChange { it.copy(readerOrientation = choice) } }
                }
            }

            Text(stringResource(R.string.load_next_chapter_ahead), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Off", 3 to "First pages", 100 to "Whole chapter").forEach { (count, label) ->
                    ChoiceChip(label, settings.prefetchPages == count) { onChange { it.copy(prefetchPages = count) } }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.keep_screen_on), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = settings.keepScreenOn, onCheckedChange = { on -> onChange { it.copy(keepScreenOn = on) } })
            }

            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (mode == ReadingMode.Vertical) "Volume keys scroll" else "Volume keys turn pages", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = settings.volumeKeys, onCheckedChange = { on -> onChange { it.copy(volumeKeys = on) } })
            }
        }
    }
}
