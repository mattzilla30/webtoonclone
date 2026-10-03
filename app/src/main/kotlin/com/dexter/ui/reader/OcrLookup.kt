package com.dexter.ui.reader

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.dexter.data.OcrEngine
import com.dexter.data.OcrResult
import kotlinx.coroutines.launch
import java.util.Locale

private val HighlightFill = Color(0x55FFC107)
private val HighlightBorder = Color(0xFFFFC107)
private val HighlightFillSelected = Color(0xAAFFC107)

/**
 * A full-screen overlay that recognizes the text on a reader page and lets the reader tap a
 * recognized block to copy, share, or translate its text. Recognition runs on demand through
 * [OcrEngine] and is cached per page, so reopening this on the same page is instant.
 */
@Composable
fun OcrLookupSheet(imageUrl: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    var japanese by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<OcrResult?>(null) }
    var selected by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(imageUrl, japanese) {
        result = null
        selected = null
        result = OcrEngine.recognizeText(context, imageUrl, japanese)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val current = result) {
            null -> LoadingState()
            else -> when {
                current.error != null -> MessageState(current.error)
                current.blocks.isEmpty() -> MessageState("No text found on this page")
                else -> PageWithHighlights(
                    imageUrl = imageUrl,
                    result = current,
                    selected = selected,
                    onSelect = { selected = it },
                )
            }
        }

        // The selected block's text and its actions.
        val blocks = result?.blocks.orEmpty()
        val block = selected?.let { blocks.getOrNull(it) }
        if (block != null) {
            Surface(
                tonalElevation = 8.dp,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(
                        block.text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                    Row {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Recognized text", block.text)))
                                }
                            },
                        ) { Text("Copy") }
                        TextButton(
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, block.text)
                                }
                                context.startActivity(Intent.createChooser(send, null))
                            },
                        ) { Text("Share") }
                        TextButton(onClick = { openTranslator(context, block.text) }) { Text("Translate") }
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                "Recognize text",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            Text("Japanese", style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Switch(checked = japanese, onCheckedChange = { japanese = it })
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(
                    "Recognizing text",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

/**
 * An OCR result with nothing to show: an error, or a page with no recognized text. The sheet's top
 * row already has a close button, so this shows no second one.
 */
@Composable
private fun MessageState(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(message, style = MaterialTheme.typography.bodyLarge, color = Color.White)
        }
    }
}

/**
 * The page with a highlight drawn over every recognized block. Tapping a highlight calls [onSelect]
 * with its index. The image box keeps the bitmap's aspect ratio, so each block's box (in bitmap
 * pixels) maps onto the shown image with plain proportional coordinates.
 */
@Composable
private fun PageWithHighlights(
    imageUrl: String,
    result: OcrResult,
    selected: Int?,
    onSelect: (Int?) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val aspect = result.bitmapWidth.toFloat() / result.bitmapHeight
        val boxWidth: Dp
        val boxHeight: Dp
        if (maxWidth / aspect <= maxHeight) {
            boxWidth = maxWidth
            boxHeight = maxWidth / aspect
        } else {
            boxHeight = maxHeight
            boxWidth = maxHeight * aspect
        }
        Box(Modifier.size(boxWidth, boxHeight).align(Alignment.Center)) {
            var boxPx by remember { mutableStateOf(IntSize.Zero) }
            AsyncImage(
                model = imageUrl,
                contentDescription = "Page",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { boxPx = it },
            )
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(result.blocks, boxPx) {
                        detectTapGestures { tap ->
                            if (boxPx.width <= 0 || boxPx.height <= 0) return@detectTapGestures
                            val hit = result.blocks.indexOfFirst { block ->
                                val left = block.boundingBox.left / result.bitmapWidth.toFloat()
                                val top = block.boundingBox.top / result.bitmapHeight.toFloat()
                                val right = block.boundingBox.right / result.bitmapWidth.toFloat()
                                val bottom = block.boundingBox.bottom / result.bitmapHeight.toFloat()
                                tap.x / boxPx.width in left..right && tap.y / boxPx.height in top..bottom
                            }
                            onSelect(if (hit >= 0) hit else null)
                        }
                    },
            ) {
                result.blocks.forEachIndexed { index, block ->
                    val w = result.bitmapWidth.toFloat()
                    val h = result.bitmapHeight.toFloat()
                    val left = block.boundingBox.left / w * size.width
                    val top = block.boundingBox.top / h * size.height
                    val right = block.boundingBox.right / w * size.width
                    val bottom = block.boundingBox.bottom / h * size.height
                    val fill = if (index == selected) HighlightFillSelected else HighlightFill
                    drawRect(fill, topLeft = Offset(left, top), size = Size(right - left, bottom - top))
                    drawRect(HighlightBorder, topLeft = Offset(left, top), size = Size(right - left, bottom - top), style = Stroke(width = 2.dp.toPx()))
                }
            }
        }
    }
}

/**
 * Opens the recognized text in a translator. When the Google Translate app is installed its
 * translate intent opens; otherwise the text opens in the web translator instead. Nothing here
 * needs a translate library.
 */
private fun openTranslator(context: Context, text: String) {
    val language = Locale.getDefault().language
    val appIntent = Intent("com.google.android.apps.translate.TRANSLATE").apply {
        putExtra("sl", "auto")
        putExtra("tl", language)
        putExtra("text", text)
    }
    // Since Android 11 the app cannot see whether Translate is installed without declaring it, so
    // it simply tries the app and falls back to the web page when nothing answers.
    try {
        context.startActivity(appIntent)
    } catch (e: ActivityNotFoundException) {
        val url = "https://translate.google.com/?sl=auto&tl=$language&text=${Uri.encode(text)}"
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }
}
