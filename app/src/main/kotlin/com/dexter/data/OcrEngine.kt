package com.dexter.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
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
    /** The recognized blocks, top to bottom. */
    val blocks: List<OcrTextBlock>,
    /** The width of the bitmap that was recognized, in pixels. */
    val bitmapWidth: Int,
    /** The height of the bitmap that was recognized, in pixels. */
    val bitmapHeight: Int,
    /** A plain-language reason for failure, or null when recognition ran. Empty [blocks] with null [error] means no text was found. */
    val error: String? = null,
)

/**
 * On-device text recognition for reader pages, powered by Tesseract, the open-source OCR engine. It
 * only runs when the reader asks for it, fetches the page through the app's shared Coil image loader
 * (so a page already on screen comes from the cache), and remembers results per page so re-opening
 * the lookup does not re-run. The language data ships in the app's assets, so it works offline.
 */
object OcrEngine {
    /** Long strips are shrunk to this on their longest side before recognition, to bound memory use. */
    private const val MAX_SIDE_PX = 2048

    /** Blocks Tesseract is less sure of than this, out of 100, are noise from artwork and are left out. */
    private const val MIN_CONFIDENCE = 40

    /** The language files in assets/tessdata. Tesseract reads them from a folder on disk. */
    private val LANGUAGE_FILES = listOf("eng", "jpn", "jpn_vert")

    private val lock = Any()
    private val cache = mutableMapOf<String, OcrResult>()

    /** One recognition at a time: Tesseract is heavy, and a second run only competes with the first. */
    private val running = Mutex()

    /**
     * Recognizes the text on the page at [imageUrl]. With [japanese], horizontal and vertical Japanese
     * are recognized; otherwise English and other Latin-script text. Results are cached per page, so
     * this is safe to call again when the lookup re-opens. Never throws: every failure comes back as
     * an [OcrResult] with [OcrResult.error] set.
     */
    suspend fun recognizeText(context: Context, imageUrl: String, japanese: Boolean = false): OcrResult {
        val cacheKey = imageUrl + if (japanese) "#ja" else ""
        synchronized(lock) { cache[cacheKey]?.let { return it } }
        val result = runCatching { recognize(context, imageUrl, japanese) }
            .getOrElse { OcrResult(emptyList(), 0, 0, "Text recognition failed") }
        if (result.error == null) synchronized(lock) { cache[cacheKey] = result }
        return result
    }

    /** Drops every cached result, so the next lookup recognizes from scratch. */
    fun clearCache() {
        synchronized(lock) { cache.clear() }
    }

    private suspend fun recognize(context: Context, imageUrl: String, japanese: Boolean): OcrResult =
        withContext(Dispatchers.Default) {
            val app = context.applicationContext
            val bitmap = loadBitmap(app, imageUrl)
                ?: return@withContext OcrResult(emptyList(), 0, 0, "Could not load the page image")
            val (scaled, scale) = downscale(bitmap)
            val dataPath = withContext(Dispatchers.IO) { installLanguages(app) }
            running.withLock {
                val tess = TessBaseAPI()
                try {
                    if (!tess.init(dataPath.absolutePath, if (japanese) "jpn+jpn_vert" else "eng")) {
                        return@withLock OcrResult(emptyList(), 0, 0, "Could not start text recognition")
                    }
                    // Speech bubbles are scattered across the page, so look for text anywhere, not in columns.
                    tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT
                    tess.setImage(scaled)
                    tess.getUTF8Text()
                    OcrResult(
                        blocks = readBlocks(tess, scale),
                        bitmapWidth = bitmap.width,
                        bitmapHeight = bitmap.height,
                    )
                } finally {
                    tess.recycle()
                }
            }
        }

    /** Every recognized block, top to bottom, with its box scaled back to the original bitmap's pixels. */
    private fun readBlocks(tess: TessBaseAPI, scale: Float): List<OcrTextBlock> {
        val level = TessBaseAPI.PageIteratorLevel.RIL_BLOCK
        val iterator = tess.resultIterator ?: return emptyList()
        val blocks = ArrayList<OcrTextBlock>()
        try {
            iterator.begin()
            do {
                val text = iterator.getUTF8Text(level)?.trim().orEmpty()
                val box = iterator.getBoundingRect(level)
                if (text.isNotEmpty() && box != null && iterator.confidence(level) >= MIN_CONFIDENCE) {
                    val inverse = 1f / scale
                    blocks += OcrTextBlock(
                        text = text,
                        boundingBox = Rect(
                            (box.left * inverse).roundToInt(),
                            (box.top * inverse).roundToInt(),
                            (box.right * inverse).roundToInt(),
                            (box.bottom * inverse).roundToInt(),
                        ),
                    )
                }
            } while (iterator.next(level))
        } finally {
            iterator.delete()
        }
        return blocks
    }

    /**
     * Copies the language files from the app's assets into files/tesseract/tessdata, where Tesseract
     * reads them, and returns files/tesseract. A file already there at the right size stays.
     */
    private fun installLanguages(context: Context): File {
        val root = File(context.filesDir, "tesseract")
        val tessdata = File(root, "tessdata").apply { mkdirs() }
        LANGUAGE_FILES.forEach { language ->
            val name = "$language.traineddata"
            val target = File(tessdata, name)
            val size = context.assets.openFd("tessdata/$name").use { it.length }
            if (target.length() != size) {
                val partial = File(tessdata, "$name.part")
                context.assets.open("tessdata/$name").use { input -> partial.outputStream().use { input.copyTo(it) } }
                partial.renameTo(target)
            }
        }
        return root
    }

    private suspend fun loadBitmap(context: Context, imageUrl: String): Bitmap? {
        // A software bitmap: recognition and the downscale below read its pixels, which a hardware bitmap forbids.
        val request = ImageRequest.Builder(context).data(imageUrl).allowHardware(false).build()
        val result = runCatching { SingletonImageLoader.get(context).execute(request) }.getOrNull()
        val bitmap = (result?.image as? BitmapImage)?.bitmap ?: return null
        // Tesseract takes 32-bit pixels only.
        return if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
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
}
