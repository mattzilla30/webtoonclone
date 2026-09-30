package com.webtoonclone.ui.reader

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.SingletonImageLoader
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import com.webtoonclone.data.Chapter
import com.webtoonclone.data.ReaderBackground
import com.webtoonclone.data.Settings
import com.webtoonclone.ui.ChoiceChip
import com.webtoonclone.ui.LoadView
import com.webtoonclone.ui.theme.Green
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
private const val NEXT_CHAPTER_PRELOAD_AT = 3
private const val NEXT_CHAPTER_PAGES = 3

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
    var barsVisible by remember { mutableStateOf(true) }
    var showOptions by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Keep the screen on while reading, and release it when the reader closes.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // With the setting on, the volume keys scroll this screen instead of changing the volume.
    DisposableEffect(settings.volumeKeys) {
        VolumeKeyPager.active = settings.volumeKeys
        onDispose { VolumeKeyPager.active = false }
    }

    val onPage = if (settings.readerBackground == ReaderBackground.White) Color.Black else Color.White

    Box(Modifier.fillMaxSize().background(readerBackgroundColor(settings.readerBackground))) {
        LoadView(state, onRetry = viewModel::retry) { page ->
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = page.startPage.coerceIn(0, (page.pages.size - 1).coerceAtLeast(0)),
            )

            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemIndex }
                    .distinctUntilChanged()
                    .collect { viewModel.saveProgress(it) }
            }

            // Fetch the next few pages ahead of the scroll so they are ready when you arrive.
            LaunchedEffect(listState, page.pages) {
                val loader = SingletonImageLoader.get(context)
                snapshotFlow { listState.firstVisibleItemIndex }
                    .distinctUntilChanged()
                    .collect { first ->
                        for (next in first + 1..first + PRELOAD_AHEAD) {
                            page.pages.getOrNull(next)?.let { url ->
                                loader.enqueue(ImageRequest.Builder(context).data(url).build())
                            }
                        }
                    }
            }

            // Near the end of the chapter, preload the first pages of the next one.
            LaunchedEffect(listState, page.pages) {
                snapshotFlow { listState.firstVisibleItemIndex >= page.pages.size - NEXT_CHAPTER_PRELOAD_AT }.first { it }
                val loader = SingletonImageLoader.get(context)
                viewModel.nextChapterPreview(NEXT_CHAPTER_PAGES).forEach { url ->
                    loader.enqueue(ImageRequest.Builder(context).data(url).build())
                }
            }

            // Volume keys move the reader by most of a screen.
            LaunchedEffect(listState) {
                VolumeKeyPager.events.collect { direction ->
                    listState.animateScrollBy(pageScrollAmount(listState.layoutInfo.viewportSize.height, direction))
                }
            }

            // Auto-scroll. A touch takes the scroll over, and then auto-scroll switches itself off.
            val level = settings.autoScrollLevel.coerceIn(0, AUTO_SCROLL_PX.lastIndex)
            LaunchedEffect(listState, level) {
                if (level == 0) return@LaunchedEffect
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

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { barsVisible = !barsVisible },
            ) {
                itemsIndexed(page.pages, key = { _, url -> url }) { index, url ->
                    // Bumping the attempt count rebuilds the image, which asks the server again.
                    var attempt by remember { mutableIntStateOf(0) }
                    key(attempt) {
                        SubcomposeAsyncImage(
                            model = url,
                            contentDescription = "Page ${index + 1}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth(),
                            loading = { Box(Modifier.fillMaxWidth().height(500.dp).background(Color(0xFF2A2A2A))) },
                            error = {
                                Box(
                                    Modifier.fillMaxWidth().height(200.dp).background(Color(0xFF2A2A2A)).clickable { attempt++ },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("Page ${index + 1} failed to load. Tap to retry.", color = Color.White)
                                }
                            },
                            success = { SubcomposeAsyncImageContent() },
                        )
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("End of Ep. ${page.chapter.number}", color = onPage, fontWeight = FontWeight.Bold)
                        if (page.nextId != null) {
                            Text(
                                "Next episode",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 16.dp).clip(RoundedCornerShape(22.dp)).background(Green)
                                    .clickable { onOpenChapter(page.nextId) }.padding(horizontal = 28.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }

            // The 1-based page at the top of the screen. It changes once per page, not once per pixel.
            val position by remember(listState, page.pages.size) {
                derivedStateOf { (listState.firstVisibleItemIndex + 1).coerceAtMost(page.pages.size) }
            }

            // Dimming sits over the pages and under the bars. It does not take touches.
            if (settings.readerDim > 0) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = settings.readerDim.coerceIn(0, 70) / 100f)))
            }

            if (!barsVisible) {
                // A small counter stays visible when the bars are hidden.
                Text(
                    "$position / ${page.pages.size}",
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.clickable(onClick = onBack))
                    Text("Ep. ${page.chapter.number}", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 16.dp))
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Reader options",
                        tint = Color.White,
                        modifier = Modifier.padding(end = 16.dp).clickable { showOptions = true },
                    )
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share",
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
                    if (page.pages.size > 1) {
                        Slider(
                            value = (position - 1).toFloat(),
                            onValueChange = { scope.launch { listState.scrollToItem(it.roundToInt()) } },
                            valueRange = 0f..(page.pages.size - 1).toFloat(),
                            colors = SliderDefaults.colors(thumbColor = Green, activeTrackColor = Green),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("$position / ${page.pages.size}", color = Color.White, fontSize = 12.sp)
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = "Chapters",
                            tint = Color.White,
                            modifier = Modifier.clickable { showChapters = true },
                        )
                        Text("#${page.index + 1}", color = Color.White, fontSize = 12.sp)
                        Row {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous episode",
                                tint = if (page.prevId != null) Color.White else Color.DarkGray,
                                modifier = Modifier.clickable(enabled = page.prevId != null) { onOpenChapter(page.prevId!!) },
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next episode",
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
                    onSelect = { showChapters = false; if (it != page.chapter.id) onOpenChapter(it) },
                    onDismiss = { showChapters = false },
                )
            }
        }

        if (showOptions) {
            ReaderOptions(settings, onChange = viewModel::updateSettings, onDismiss = { showOptions = false })
        }
    }
}

/** Every readable chapter, oldest first, opened at the current one. Tapping one jumps to it. */
@Composable
private fun ChapterPicker(chapters: List<Chapter>, currentId: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = chapters.indexOfFirst { it.id == currentId }.coerceAtLeast(0))
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(vertical = 12.dp)) {
            Text("Chapters", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
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
                        color = if (current) Green else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(chapter.id) }.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/** Dimming, background, auto-scroll speed, and volume-key paging, saved as you change them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderOptions(settings: Settings, onChange: ((Settings) -> Settings) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(20.dp)) {
            Text("Reader options", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Text("Dimming", fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
            Slider(
                value = settings.readerDim.toFloat(),
                onValueChange = { value -> onChange { it.copy(readerDim = value.roundToInt()) } },
                valueRange = 0f..70f,
                colors = SliderDefaults.colors(thumbColor = Green, activeTrackColor = Green),
            )

            Text("Background", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderBackground.entries.forEach { choice ->
                    ChoiceChip(choice.name, settings.readerBackground == choice) { onChange { it.copy(readerBackground = choice) } }
                }
            }

            Text("Auto-scroll", fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..5).forEach { speed ->
                    ChoiceChip(if (speed == 0) "Off" else speed.toString(), settings.autoScrollLevel == speed) {
                        onChange { it.copy(autoScrollLevel = speed) }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Volume keys scroll", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = settings.volumeKeys, onCheckedChange = { on -> onChange { it.copy(volumeKeys = on) } })
            }

            Text(
                "Done",
                color = Green,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.End).clickable(onClick = onDismiss).padding(top = 16.dp, start = 16.dp),
            )
        }
    }
}
