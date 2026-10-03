package com.dexter.data

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/** Longest side of the downscaled sample, in pixels. Small enough to cost almost nothing. */
private const val COLOR_SAMPLE_SIZE = 32

/** A page counts as color when its mean saturation reaches this. Manga ink sits far below it. */
private const val COLOR_MEAN_SATURATION = 0.14f

/** ...or when this share of its pixels is strongly saturated, for pages that are mostly white. */
private const val COLOR_SATURATED_SHARE = 0.03f

/** A pixel counts as strongly saturated from this saturation up. */
private const val STRONG_SATURATION = 0.35f

/**
 * True when [bitmap] is a color page. Samples a tiny downscale and measures saturation, so a
 * greyscale manga page (ink on paper) reads near zero while a color page reads well above the
 * threshold. Cheap enough to run for every page as it loads.
 */
fun isColorful(bitmap: Bitmap): Boolean {
    // A hardware bitmap's pixels cannot be read. Pass a software copy, such as the reader's page sample.
    if (bitmap.config == Bitmap.Config.HARDWARE) return false
    val w = bitmap.width.coerceAtLeast(1)
    val h = bitmap.height.coerceAtLeast(1)
    val scale = COLOR_SAMPLE_SIZE / max(w, h).toFloat()
    val sw = (w * scale).toInt().coerceAtLeast(1)
    val sh = (h * scale).toInt().coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
    val pixels = IntArray(sw * sh)
    small.getPixels(pixels, 0, sw, 0, 0, sw, sh)
    if (small !== bitmap) small.recycle()
    var saturationSum = 0.0
    var strong = 0
    var n = 0
    for (pixel in pixels) {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        val strongest = max(r, max(g, b))
        val weakest = min(r, min(g, b))
        if (strongest == 0) continue
        val saturation = (strongest - weakest).toFloat() / strongest
        saturationSum += saturation
        if (saturation >= STRONG_SATURATION) strong++
        n++
    }
    if (n == 0) return false
    return saturationSum / n >= COLOR_MEAN_SATURATION || strong.toFloat() / n >= COLOR_SATURATED_SHARE
}
