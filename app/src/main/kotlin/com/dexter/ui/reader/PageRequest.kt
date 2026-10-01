package com.dexter.ui.reader

import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.transformations

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
 */
fun pageRequest(context: PlatformContext, url: String, crop: Boolean = false): ImageRequest {
    val key = pageCacheKey(url)
    return ImageRequest.Builder(context).data(url)
        .memoryCacheKey(if (crop) "$key#crop" else key)
        .diskCacheKey(key)
        .apply { if (crop) transformations(CropBorders()) }
        .build()
}
