package com.dexter.ui.reader

import android.app.Activity
import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.mediarouter.app.MediaRouteButton
import com.dexter.R
import com.dexter.cast.CastManager
import com.dexter.platform.enterReaderPiP
import com.dexter.ui.SyncedSlider
import com.google.android.gms.cast.framework.CastButtonFactory
import kotlin.math.roundToInt

/**
 * The bar across the top of the reader. Which buttons it holds, and in what order, comes from
 * [actions]; the default is the layout the bar shipped with. New actions ([ToolbarAction.Thumbnails],
 * [ToolbarAction.SleepTimer], [ToolbarAction.Binge]) report through [onToolbarAction].
 */
@Composable
internal fun ReaderTopBar(
    segment: ChapterSegment,
    seriesTitle: String?,
    bookmarked: Boolean,
    incognito: Boolean,
    onBookmark: () -> Unit,
    onBack: () -> Unit,
    onOpenOptions: () -> Unit,
    castManager: CastManager? = null,
    modifier: Modifier = Modifier,
    actions: List<ToolbarAction> = defaultTopActions,
    onToolbarAction: (ToolbarAction) -> Unit = {},
    /** The read-aloud toggle shows when TTS is enabled in the accessibility settings. */
    showNarration: Boolean = false,
    narrationRunning: Boolean = false,
    /** The voice-control mic button, shown when voice control is enabled. */
    voiceButton: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val barIcons = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface, disabledContentColor = Color.DarkGray)
    Surface(
        color = barColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge.copy(topStart = CornerSize(0.dp), topEnd = CornerSize(0.dp)),
        modifier = modifier.fillMaxWidth(),
    ) {
        // The panel colour reaches up behind the status bar, and the buttons sit below it.
        Row(Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            actions.forEach { action ->
                when (action) {
                    ToolbarAction.Back -> IconButton(onClick = onBack, colors = barIcons) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                    ToolbarAction.Title -> Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            if (incognito) "Ep. ${segment.chapter.number} · Incognito" else "Ep. ${segment.chapter.number}",
                            style = MaterialTheme.typography.titleMediumEmphasized,
                        )
                        seriesTitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    ToolbarAction.Bookmark -> IconButton(onClick = onBookmark, colors = barIcons) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark this page",
                            tint = if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                    ToolbarAction.ReaderOptions -> IconButton(onClick = onOpenOptions, colors = barIcons) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.reader_options))
                    }
                    ToolbarAction.Share -> IconButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, (seriesTitle?.let { "$it, Ep. ${segment.chapter.number}\n" }.orEmpty()) + "https://mangadex.org/chapter/${segment.chapter.id}")
                            }
                            context.startActivity(Intent.createChooser(send, null))
                        },
                        colors = barIcons,
                    ) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                    }
                    ToolbarAction.Cast -> if (castManager != null) {
                        val castAvailable by castManager.isAvailable.collectAsStateWithLifecycle()
                        if (castAvailable) {
                            AndroidView(
                                factory = { ctx ->
                                    MediaRouteButton(ctx).apply {
                                        CastButtonFactory.setUpMediaRouteButton(ctx, this)
                                    }
                                },
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    }
                    ToolbarAction.Pip -> IconButton(onClick = { (context as? Activity)?.enterReaderPiP() }, colors = barIcons) {
                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = "Picture in picture")
                    }
                    ToolbarAction.Thumbnails -> IconButton(onClick = { onToolbarAction(ToolbarAction.Thumbnails) }, colors = barIcons) {
                        Icon(Icons.Default.GridView, contentDescription = "Chapter thumbnails")
                    }
                    ToolbarAction.SleepTimer -> IconButton(onClick = { onToolbarAction(ToolbarAction.SleepTimer) }, colors = barIcons) {
                        Icon(Icons.Default.Alarm, contentDescription = "Sleep timer")
                    }
                    ToolbarAction.Binge -> IconButton(onClick = { onToolbarAction(ToolbarAction.Binge) }, colors = barIcons) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Binge mode")
                    }
                    ToolbarAction.Narration -> if (showNarration) {
                        IconButton(onClick = { onToolbarAction(ToolbarAction.Narration) }, colors = barIcons) {
                            Icon(
                                if (narrationRunning) Icons.Default.Stop else Icons.Default.RecordVoiceOver,
                                contentDescription = if (narrationRunning) "Stop reading aloud" else "Read aloud",
                            )
                        }
                    }
                    // The slider, the counter, and the chapter buttons live in the bottom bar.
                    ToolbarAction.PageSlider, ToolbarAction.PageCounter, ToolbarAction.ChapterList,
                    ToolbarAction.PrevChapter, ToolbarAction.NextChapter,
                    -> Unit
                }
            }
            // The read-aloud toggle is always within reach when TTS is on, even when the
            // configurable toolbar does not include it.
            if (showNarration && ToolbarAction.Narration !in actions) {
                IconButton(onClick = { onToolbarAction(ToolbarAction.Narration) }, colors = barIcons) {
                    Icon(
                        if (narrationRunning) Icons.Default.Stop else Icons.Default.RecordVoiceOver,
                        contentDescription = if (narrationRunning) "Stop reading aloud" else "Read aloud",
                    )
                }
            }
            voiceButton?.invoke()
        }
    }
}

/**
 * The bar along the bottom of the reader. [actions] chooses its buttons and their order; the page
 * slider shows a live page preview while dragging when [pageUrls] and [scrubberPreview] allow it.
 * Tapping the page counter runs [onPageCounter], which opens the thumbnail grid or the go-to-page
 * dialog depending on the reader UI settings.
 */
@Composable
internal fun ReaderBottomBar(
    segment: ChapterSegment,
    position: Int,
    count: Int,
    rtl: Boolean,
    onSeek: (Int) -> Unit,
    onJump: () -> Unit,
    onChapters: () -> Unit,
    onOpenChapter: (String) -> Unit,
    modifier: Modifier = Modifier,
    // One-handed mode hides the top bar, so its essentials move down here, near the thumb.
    oneHanded: Boolean = false,
    onBack: () -> Unit = {},
    bookmarked: Boolean = false,
    onBookmark: () -> Unit = {},
    onOpenOptions: () -> Unit = {},
    actions: List<ToolbarAction> = defaultBottomActions,
    /** The chapter's page URLs, for the slider's live preview. Empty means no preview. */
    pageUrls: List<String> = emptyList(),
    scrubberPreview: Boolean = true,
    onPageCounter: () -> Unit = onJump,
    onToolbarAction: (ToolbarAction) -> Unit = {},
    topActions: List<ToolbarAction> = defaultTopActions,
) {
    val view = LocalView.current
    val barIcons = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface, disabledContentColor = Color.DarkGray)
    Surface(
        color = barColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.navigationBarsPadding()) {
            if (oneHanded) {
                // The top bar's essentials move down here, in the user's own top-bar order.
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TopBarEssentials(
                        segment = segment,
                        bookmarked = bookmarked,
                        onBookmark = onBookmark,
                        onBack = onBack,
                        onOpenOptions = onOpenOptions,
                        barIcons = barIcons,
                        actions = topActions,
                    )
                }
            }
            if (ToolbarAction.PageSlider in actions && count > 1) {
                // Right-to-left reading puts the first page on the right, so the slider runs that way too.
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LocalLayoutDirection.current) {
                    if (scrubberPreview && pageUrls.size == count) {
                        PageScrubber(
                            position = position,
                            count = count,
                            pageUrl = { pageUrls.getOrNull(it) },
                            onSeek = onSeek,
                            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
                        )
                    } else {
                        SyncedSlider(
                            value = position.toFloat(),
                            onValueChange = { onSeek(it.roundToInt()) },
                            valueRange = 0f..(count - 1).coerceAtLeast(0).toFloat(),
                            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (ToolbarAction.PageCounter in actions) {
                    TextButton(onClick = onPageCounter) { Text("${position + 1} / $count", style = MaterialTheme.typography.labelLarge) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    actions.forEach { action ->
                        when (action) {
                            ToolbarAction.ChapterList -> IconButton(onClick = onChapters, colors = barIcons) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.chapters))
                            }
                            ToolbarAction.PrevChapter -> IconButton(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onOpenChapter(segment.prevId!!)
                                },
                                enabled = segment.prevId != null,
                                colors = barIcons,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_episode))
                            }
                            ToolbarAction.NextChapter -> IconButton(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onOpenChapter(segment.nextId!!)
                                },
                                enabled = segment.nextId != null,
                                colors = barIcons,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_episode))
                            }
                            ToolbarAction.Thumbnails -> IconButton(onClick = { onToolbarAction(ToolbarAction.Thumbnails) }, colors = barIcons) {
                                Icon(Icons.Default.GridView, contentDescription = "Chapter thumbnails")
                            }
                            ToolbarAction.SleepTimer -> IconButton(onClick = { onToolbarAction(ToolbarAction.SleepTimer) }, colors = barIcons) {
                                Icon(Icons.Default.Alarm, contentDescription = "Sleep timer")
                            }
                            ToolbarAction.Binge -> IconButton(onClick = { onToolbarAction(ToolbarAction.Binge) }, colors = barIcons) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Binge mode")
                            }
                            ToolbarAction.Bookmark -> IconButton(onClick = onBookmark, colors = barIcons) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark this page",
                                    tint = if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                )
                            }
                            ToolbarAction.ReaderOptions -> IconButton(onClick = onOpenOptions, colors = barIcons) {
                                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.reader_options))
                            }
                            // The slider, the counter, and the top-only buttons have no place in this row. The top bar
                            // always carries the read-aloud toggle when it is on.
                            ToolbarAction.PageSlider, ToolbarAction.PageCounter, ToolbarAction.Back, ToolbarAction.Title,
                            ToolbarAction.Share, ToolbarAction.Cast, ToolbarAction.Pip, ToolbarAction.Narration,
                            -> Unit
                        }
                    }
                }
            }
        }
    }
}

/**
 * The top bar's essentials for one-handed mode, rendered into the bottom bar in the user's own
 * top-bar order. Only the buttons that make sense near the thumb are kept.
 */
@Composable
private fun RowScope.TopBarEssentials(
    segment: ChapterSegment,
    bookmarked: Boolean,
    onBookmark: () -> Unit,
    onBack: () -> Unit,
    onOpenOptions: () -> Unit,
    barIcons: IconButtonColors,
    actions: List<ToolbarAction>,
) {
    val context = LocalContext.current
    actions.forEach { action ->
        when (action) {
            ToolbarAction.Back -> IconButton(onClick = onBack, colors = barIcons) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            ToolbarAction.Title -> Text(
                "Ep. ${segment.chapter.number}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            ToolbarAction.Bookmark -> IconButton(onClick = onBookmark, colors = barIcons) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark this page",
                    tint = if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
            ToolbarAction.ReaderOptions -> IconButton(onClick = onOpenOptions, colors = barIcons) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.reader_options))
            }
            ToolbarAction.Share -> IconButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "https://mangadex.org/chapter/${segment.chapter.id}")
                    }
                    context.startActivity(Intent.createChooser(send, null))
                },
                colors = barIcons,
            ) {
                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
            }
            else -> Unit
        }
    }
}

/** Asks for a page number and calls [onGo] with the 0-based page when confirmed. */
@Composable
internal fun GoToPageDialog(typed: String, count: Int, onChange: (String) -> Unit, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
    val view = LocalView.current
    val target = typed.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to page") },
        text = {
            OutlinedTextField(
                value = typed,
                onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
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
                    onGo((target ?: 1) - 1)
                    onDismiss()
                },
            ) { Text("Go") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
