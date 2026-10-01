package com.dexter.ui.reader

import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import com.dexter.R
import com.dexter.data.Settings
import com.dexter.ui.SyncedSlider
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/** The bar across the top of the reader: back, the episode and series, options, and share. */
@Composable
internal fun ReaderTopBar(page: ReaderPage, onBack: () -> Unit, onOpenOptions: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val barIcons = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface, disabledContentColor = Color.DarkGray)
    Surface(
        color = barColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge.copy(topStart = CornerSize(0.dp), topEnd = CornerSize(0.dp)),
        modifier = modifier.fillMaxWidth(),
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
}

/** The bar along the bottom of the reader: a page slider, the page count, the chapter list, and previous and next episode. */
@Composable
internal fun ReaderBottomBar(
    page: ReaderPage,
    position: Int,
    count: Int,
    rtl: Boolean,
    onSeek: (Int) -> Unit,
    onJump: () -> Unit,
    onChapters: () -> Unit,
    onOpenChapter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val barIcons = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface, disabledContentColor = Color.DarkGray)
    Surface(
        color = barColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            if (count > 1) {
                // Right-to-left reading puts the first page on the right, so the slider runs that way too.
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LocalLayoutDirection.current) {
                    SyncedSlider(
                        value = position.toFloat(),
                        onValueChange = { onSeek(it.roundToInt()) },
                        valueRange = 0f..(count - 1).coerceAtLeast(0).toFloat(),
                        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onJump) { Text("${position + 1} / $count", style = MaterialTheme.typography.labelLarge) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onChapters, colors = barIcons) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.chapters))
                    }
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onOpenChapter(page.prevId!!)
                        },
                        enabled = page.prevId != null,
                        colors = barIcons,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_episode))
                    }
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onOpenChapter(page.nextId!!)
                        },
                        enabled = page.nextId != null,
                        colors = barIcons,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_episode))
                    }
                }
            }
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
