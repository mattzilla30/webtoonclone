package com.dexter.data

import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.File

private const val API_MAX_AGE_SECONDS = 60
private const val TAG_MAX_AGE_SECONDS = 7 * 24 * 60 * 60
private const val CACHE_BYTES = 10L * 1024 * 1024

/**
 * The Cache-Control header to give an API response. Responses are reusable for a minute, which
 * spares repeat requests when you go back to a screen. The at-home endpoint hands out page image
 * servers that expire, and MangaDex's own headers would let it be cached, so it is set to no-store.
 * The random endpoint is no-store too, or tapping Random twice in a minute would repeat a series.
 * The tag list almost never changes, so it is kept for a week on disk, which saves a request per launch.
 */
fun cacheControlFor(encodedPath: String): String = when {
    encodedPath.startsWith("/at-home") || encodedPath == "/manga/random" -> "no-store"
    encodedPath == "/manga/tag" -> "public, max-age=$TAG_MAX_AGE_SECONDS"
    else -> "public, max-age=$API_MAX_AGE_SECONDS"
}

/** An HTTP client that keeps API responses for a minute in [directory]. */
fun cachingClient(directory: File): OkHttpClient =
    OkHttpClient.Builder()
        .cache(Cache(directory, CACHE_BYTES))
        .addNetworkInterceptor(
            Interceptor { chain ->
                val response = chain.proceed(chain.request())
                response.newBuilder()
                    .removeHeader("Pragma")
                    .header("Cache-Control", cacheControlFor(chain.request().url.encodedPath))
                    .build()
            },
        )
        .build()
