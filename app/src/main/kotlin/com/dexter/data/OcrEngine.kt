package com.dexter.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/** One chunk of recognized text, with its location in the page bitmap's pixels. */
data class OcrTextBlock(
    /** The recognized text. */
    val text: String,
    /** Where the text sits, in the page bitmap's pixels (see [OcrResult.bitmapWidth]/[OcrResult.bitmapHeight]). */
    val boundingBox: Rect,
)

/** The outcome of recognizing the text on one page. Never thrown: failures carry [error] instead. */
data class OcrResult(
    /** The recognized blocks, in reading order when ML Kit provides it. */
    val blocks: List<OcrTextBlock>,
    /** The width of the bitmap that was recognized, in pixels. */
    val bitmapWidth: Int,
    /** The height of the bitmap that was recognized, in pixels. */
    val bitmapHeight: Int,
    /** A plain-language reason for failure, or null when recognition ran. Empty [blocks] with null [error] means no text was found. */
    val error: String? = null,
)

/**
 * On-device text recognition for reader pages, powered by ML Kit. It only runs when the reader asks
 * for it, fetches the page through the app's shared Coil image loader (so a page already on screen
 * comes from the cache), and remembers results per page so re-opening the lookup does not re-run.
 */
object OcrEngine {
    /** Long strips are shrunk to this on their longest side before recognition, to bound memory use. */
    private const val MAX_SIDE_PX = 2048

    private val lock = Any()
    private val cache = mutableMapOf<String, OcrResult>()

    /**
     * Recognizes the text on the page at [imageUrl]. With [japanese], kanji, hiragana, and katakana
     * are recognized too; otherwise the Latin recognizer runs. Results are cached per page, so this
     * is safe to call again when the lookup re-opens. Never throws: every failure comes back as an
     * [OcrResult] with [OcrResult.error] set.
     */
    suspend fun recognizeText(context: Context, imageUrl: String, japanese: Boolean = false): OcrResult {
        val cacheKey = imageUrl + if (japanese) "#ja" else ""
        synchronized(lock) { cache[cacheKey]?.let { return it } }
        val result = runCatching { recognize(context, imageUrl, japanese) }
            .getOrElse { OcrResult(emptyList(), 0, 0, "Text recognition failed") }
        synchronized(lock) { cache[cacheKey] = result }
        return result
    }

    /** Drops every cached result, so the next lookup recognizes from scratch. */
    fun clearCache() {
        synchronized(lock) { cache.clear() }
    }

    private suspend fun recognize(context: Context, imageUrl: String, japanese: Boolean): OcrResult =
        withContext(Dispatchers.Default) {
            val bitmap = loadBitmap(context.applicationContext, imageUrl)
                ?: return@withContext OcrResult(emptyList(), 0, 0, "Could not load the page image")
            val (scaled, scale) = downscale(bitmap)
            val options = if (japanese) JapaneseTextRecognizerOptions.Builder().build() else TextRecognizerOptions.DEFAULT_OPTIONS
            val recognizer = TextRecognition.getClient(options)
            try {
                val text = recognizer.recognizeSuspend(InputImage.fromBitmap(scaled, 0))
                OcrResult(
                    blocks = text.textBlocks.mapNotNull { it.toBlock(scale) },
                    bitmapWidth = bitmap.width,
                    bitmapHeight = bitmap.height,
                )
            } finally {
                recognizer.close()
            }
        }

    private suspend fun loadBitmap(context: Context, imageUrl: String): Bitmap? {
        val request = ImageRequest.Builder(context).data(imageUrl).build()
        val result = runCatching { SingletonImageLoader.get(context).execute(request) }.getOrNull()
        return (result?.image as? BitmapImage)?.bitmap
    }

    /** Shrinks [bitmap] so its longest side is at most [MAX_SIDE_PX], returning the scaled bitmap and its scale. */
    private fun downscale(bitmap: Bitmap): Pair<Bitmap, Float> {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_SIDE_PX) return bitmap to 1f
        val scale = MAX_SIDE_PX / longest.toFloat()
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt(),
            (bitmap.height * scale).roundToInt(),
            true,
        )
        return scaled to scale
    }

    /** A text block with its box scaled back to the original bitmap's pixels, or null when it has no usable text or box. */
    private fun Text.TextBlock.toBlock(scale: Float): OcrTextBlock? {
        val box = boundingBox ?: return null
        if (text.isBlank()) return null
        val inverse = 1f / scale
        return OcrTextBlock(
            text = text,
            boundingBox = Rect(
                (box.left * inverse).roundToInt(),
                (box.top * inverse).roundToInt(),
                (box.right * inverse).roundToInt(),
                (box.bottom * inverse).roundToInt(),
            ),
        )
    }

    private suspend fun TextRecognizer.recognizeSuspend(image: InputImage): Text =
        suspendCancellableCoroutine { cont ->
            process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
}
