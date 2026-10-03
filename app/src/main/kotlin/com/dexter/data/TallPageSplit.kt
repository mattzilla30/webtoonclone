package com.dexter.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import coil3.size.Size
import coil3.transform.Transformation

// Tall-page splitting for vertical webtoons. A single 800x20000 strip is unreadable when scaled to
// fit the screen width; splitting it into screen-height segments keeps text legible. The math here
// is pure image logic: [splitTallPage] turns a pixel height into segment fractions, and
// [SplitSegment] is a Coil transformation that crops the decoded bitmap to one segment, so the
// reader's existing AsyncImage pipeline shows each segment as its own item.

/** One vertical slice of a page, as fractions of the page height. */
data class PageSegment(val topFraction: Float, val bottomFraction: Float, val index: Int, val count: Int)

/**
 * Splits a page [heightPx] tall into segments no taller than [maxSegmentHeightPx]. Returns a single
 * full-page segment when the page already fits. Segments divide the page evenly, so no slice is a
 * sliver.
 */
fun splitTallPage(heightPx: Int, maxSegmentHeightPx: Int): List<PageSegment> {
    if (heightPx <= 0 || maxSegmentHeightPx <= 0 || heightPx <= maxSegmentHeightPx) {
        return listOf(PageSegment(0f, 1f, 0, 1))
    }
    val count = (heightPx + maxSegmentHeightPx - 1) / maxSegmentHeightPx
    return (0 until count).map { i ->
        PageSegment(i / count.toFloat(), (i + 1) / count.toFloat(), i, count)
    }
}

/**
 * The pixel height of the image at [path], decoded from its bounds only. Null when the file is not
 * a readable image. Used to decide splits before the full bitmap is ever loaded.
 */
fun imageHeightPx(path: String): Int? = try {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, options)
    options.outHeight.takeIf { it > 0 }
} catch (e: Exception) {
    null
}

/**
 * Crops the decoded page bitmap to [segment]. Compose it after [com.dexter.ui.reader.CropBorders]
 * when both are on: crop borders first, then split, so each segment keeps the trimmed edges.
 */
class SplitSegment(val segment: PageSegment) : Transformation() {
    override val cacheKey: String = "tall-split-${segment.index}-of-${segment.count}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (segment.count <= 1) return input
        val top = (input.height * segment.topFraction).toInt().coerceIn(0, input.height - 1)
        val bottom = (input.height * segment.bottomFraction).toInt().coerceIn(top + 1, input.height)
        if (top == 0 && bottom == input.height) return input
        return Bitmap.createBitmap(input, 0, top, input.width, bottom - top)
    }
}

/**
 * The split plan for a chapter's pages: page index to its segments. The reader renders one item
 * per segment and keeps the original page number for progress, so splitting never corrupts the
 * saved position. Compute [maxSegmentHeightPx] as screen height times the user's threshold.
 */
fun splitPlan(pageHeights: Map<Int, Int>, maxSegmentHeightPx: Int): Map<Int, List<PageSegment>> =
    pageHeights.mapValues { (_, height) -> splitTallPage(height, maxSegmentHeightPx) }
        .filterValues { it.size > 1 }
