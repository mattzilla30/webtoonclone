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
 * Trims plain margins from a page: rows and columns at the edges that are all close to the corner colour,
 * white or black. Every fourth pixel is sampled, which is plenty for a flat margin.
 */
class CropBorders : Transformation() {
    override val cacheKey: String = "crop-borders"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = input.width
        val height = input.height
        if (width < 16 || height < 16) return input
        val margin = input[0, 0]
        val row = IntArray(width)
        val column = IntArray(height)
        fun near(color: Int) = abs(((color shr 16) and 0xFF) - ((margin shr 16) and 0xFF)) <= MARGIN_TOLERANCE &&
            abs(((color shr 8) and 0xFF) - ((margin shr 8) and 0xFF)) <= MARGIN_TOLERANCE &&
            abs((color and 0xFF) - (margin and 0xFF)) <= MARGIN_TOLERANCE
        val rowIsMargin = { y: Int ->
            input.getPixels(row, 0, width, 0, y, width, 1)
            (0 until width step 4).all { near(row[it]) }
        }
        val columnIsMargin = { x: Int ->
            input.getPixels(column, 0, 1, x, 0, 1, height)
            (0 until height step 4).all { near(column[it]) }
        }
        val (top, bottom) = marginRun(height, (height * MAX_CROP_SHARE).toInt(), rowIsMargin)
        val (left, right) = marginRun(width, (width * MAX_CROP_SHARE).toInt(), columnIsMargin)
        if (top + bottom + left + right == 0) return input
        return Bitmap.createBitmap(input, left, top, width - left - right, height - top - bottom)
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
