package com.dexter.ui.reader

import android.graphics.Bitmap
import coil3.BitmapImage
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.transformations
import com.dexter.data.PageSegment
import com.dexter.data.SplitSegment

/**
 * The cache key for a page image. MangaDex serves a page from different servers over time, but the
 * part from "/data/" or "/data-saver/" on stays the same, so a page found again under new addresses
 * comes from the cache instead of the network. Saved pages and other addresses keep their full address.
 */
fun pageCacheKey(url: String): String {
    if (!url.startsWith("http")) return url
    val start = listOf("/data/", "/data-saver/").map { url.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: return url
    return url.substring(start)
}

/**
 * A small software copy of a page, for reading its colours. Shown pages are hardware bitmaps, whose
 * pixels cannot be read, so the samplers decode their own 64 px copy from the disk cache instead.
 */
suspend fun pageSample(context: PlatformContext, url: String): Bitmap? {
    val request = ImageRequest.Builder(context).data(url)
        .memoryCacheKey(pageCacheKey(url) + "#sample")
        .diskCacheKey(pageCacheKey(url))
        .size(SAMPLE_PX)
        .allowHardware(false)
        .build()
    return (SingletonImageLoader.get(context).execute(request).image as? BitmapImage)?.bitmap
}

private const val SAMPLE_PX = 64

/**
 * A request for one page, cached under [pageCacheKey]. Showing a page and loading it ahead use the same keys.
 * With [crop], plain margins are trimmed, and the trimmed copy keeps its own place in the memory cache.
 * With [segment], only that chunk of a split tall page is decoded, under its own cache key.
 */
fun pageRequest(context: PlatformContext, url: String, crop: Boolean = false, segment: PageSegment? = null): ImageRequest {
    val key = pageCacheKey(url)
    val memoryKey = when {
        segment != null -> "$key#split${segment.index}"
        crop -> "$key#crop2"
        else -> key
    }
    return ImageRequest.Builder(context).data(url)
        .memoryCacheKey(memoryKey)
        .diskCacheKey(key)
        .transformations(
            listOfNotNull(
                CropBorders().takeIf { crop },
                segment?.let { SplitSegment(it) },
            ),
        )
        .build()
}
