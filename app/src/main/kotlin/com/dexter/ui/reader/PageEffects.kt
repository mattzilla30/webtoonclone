package com.dexter.ui.reader

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.core.graphics.get
import coil3.size.Size
import coil3.transform.Transformation
import com.dexter.data.ReaderFilter
import kotlin.math.abs

/** How far a pixel may differ from the margin colour and still count as margin, per channel, out of 255. */
private const val MARGIN_TOLERANCE = 24

/** Never crop away more than this share of a side, so a mostly white page is not cut down to a sliver. */
private const val MAX_CROP_SHARE = 0.25f

/**
 * A row or column counts as page content when it holds an unbroken line of ink this long, as a share
 * of its length: a panel border, a speech bubble's outline, or artwork. Text is broken into separate
 * letters, so a page number or a translator's note in the margin never forms one, and is trimmed.
 */
private const val CONTENT_RUN_SHARE = 0.08f

/** A row or column this inked overall also counts as content, for dense art without long strokes. */
private const val CONTENT_INK_SHARE = 0.2f

/** How much margin to keep outside the content, as a share of the side, so panel borders are not cut flush. */
private const val KEEP_SHARE = 0.006f

/** The most pixels sampled along a side. Enough to find margins, and cheap even for a 20,000 px webtoon strip. */
private const val SAMPLES_PER_SIDE = 600

/**
 * The rows or columns to trim from each end of a line of pixels: how many leading and trailing entries
 * of [isMargin] are true, each capped at [max].
 */
fun marginRun(count: Int, max: Int, isMargin: (Int) -> Boolean): Pair<Int, Int> {
    var start = 0
    while (start < max && start < count && isMargin(start)) start++
    var end = 0
    while (end < max && count - 1 - end > start && isMargin(count - 1 - end)) end++
    return start to end
}

/**
 * How much to trim from each side of a [width] by [height] page, as left, top, right, bottom. [isInk]
 * says whether a pixel is content rather than margin. Columns and rows are scanned in from each edge
 * until one counts as content (see [CONTENT_RUN_SHARE] and [CONTENT_INK_SHARE]), so marks in the
 * margin do not stop the trim. [stepX] and [stepY] sample every so many pixels.
 */
fun contentTrim(width: Int, height: Int, stepX: Int = 1, stepY: Int = 1, isInk: (x: Int, y: Int) -> Boolean): IntArray {
    val columns = (width + stepX - 1) / stepX
    val rows = (height + stepY - 1) / stepY
    val columnInk = IntArray(columns)
    val rowInk = IntArray(rows)
    val columnRun = IntArray(columns)
    val columnBest = IntArray(columns)
    val rowBest = IntArray(rows)
    for (j in 0 until rows) {
        var run = 0
        for (i in 0 until columns) {
            if (isInk(i * stepX, j * stepY)) {
                columnInk[i]++
                rowInk[j]++
                run++
                if (run > rowBest[j]) rowBest[j] = run
                columnRun[i]++
                if (columnRun[i] > columnBest[i]) columnBest[i] = columnRun[i]
            } else {
                run = 0
                columnRun[i] = 0
            }
        }
    }
    fun trim(ink: IntArray, best: IntArray, across: Int, step: Int, size: Int): Pair<Int, Int> {
        val longRun = (across * CONTENT_RUN_SHARE).coerceAtLeast(2f)
        val heavy = (across * CONTENT_INK_SHARE).coerceAtLeast(2f)
        val max = (ink.size * MAX_CROP_SHARE).toInt()
        val (start, end) = marginRun(ink.size, max) { best[it] < longRun && ink[it] < heavy }
        val keep = (size * KEEP_SHARE).toInt()
        val startPx = (start * step - keep).coerceAtLeast(0)
        // The last sampled column of the content ends one step further in than its index.
        val endPx = if (end == 0) 0 else (size - ((ink.size - end) * step) - keep).coerceAtLeast(0)
        return startPx to endPx
    }
    val (left, right) = trim(columnInk, columnBest, rows, stepX, width)
    val (top, bottom) = trim(rowInk, rowBest, columns, stepY, height)
    return intArrayOf(left, top, right, bottom)
}

/**
 * Trims the unused edges of a page, so the art fills more of a phone screen. The margin colour comes
 * from the page's corners, white or black. A margin may hold a page number or a short note and still
 * be trimmed. A page whose art runs to its edges has no margin to find, and stays whole.
 */
class CropBorders : Transformation() {
    // A new key, so pages cached under the plain-margin trim are cropped again the smarter way.
    override val cacheKey: String = "crop-content-v2"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = input.width
        val height = input.height
        if (width < 16 || height < 16) return input
        // The margin colour is the one most corners share, so art in one corner does not mislead it.
        val corners = listOf(input[0, 0], input[width - 1, 0], input[0, height - 1], input[width - 1, height - 1])
        val margin = corners.groupingBy { it }.eachCount().maxBy { it.value }.key
        fun ink(color: Int) = abs(((color shr 16) and 0xFF) - ((margin shr 16) and 0xFF)) > MARGIN_TOLERANCE ||
            abs(((color shr 8) and 0xFF) - ((margin shr 8) and 0xFF)) > MARGIN_TOLERANCE ||
            abs((color and 0xFF) - (margin and 0xFF)) > MARGIN_TOLERANCE
        val stepX = (width / SAMPLES_PER_SIDE).coerceAtLeast(1)
        val stepY = (height / SAMPLES_PER_SIDE).coerceAtLeast(1)
        val row = IntArray(width)
        var loadedRow = -1
        val (left, top, right, bottom) = contentTrim(width, height, stepX, stepY) { x, y ->
            if (y != loadedRow) {
                input.getPixels(row, 0, width, 0, y, width, 1)
                loadedRow = y
            }
            ink(row[x])
        }.toList()
        if (left + top + right + bottom == 0) return input
        val cropWidth = width - left - right
        val cropHeight = height - top - bottom
        if (cropWidth < width / 2 || cropHeight < height / 2) return input
        return Bitmap.createBitmap(input, left, top, cropWidth, cropHeight)
    }
}

/** The colour filter for [filter], or null for none. */
fun readerColorFilter(filter: ReaderFilter): ColorFilter? = when (filter) {
    ReaderFilter.None -> null
    ReaderFilter.Grayscale -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    ReaderFilter.Sepia -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    // Less blue and a little less green, for reading at night.
    ReaderFilter.Warm -> ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(1f, 0.88f, 0.68f, 1f) })
    ReaderFilter.Invert -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}
