package com.dexter.ui.reader

import coil3.PlatformContext
import coil3.request.ImageRequest
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
 * A request for one page, cached under [pageCacheKey]. Showing a page and loading it ahead use the same keys.
 * With [crop], plain margins are trimmed, and the trimmed copy keeps its own place in the memory cache.
 * With [segment], only that chunk of a split tall page is decoded, under its own cache key.
 */
fun pageRequest(context: PlatformContext, url: String, crop: Boolean = false, segment: PageSegment? = null): ImageRequest {
    val key = pageCacheKey(url)
    val memoryKey = when {
        segment != null -> "$key#split${segment.index}"
        crop -> "$key#crop"
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
