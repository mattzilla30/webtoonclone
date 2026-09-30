package com.dexter.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

private const val MAX_ATTEMPTS = 3
private const val MAX_RETRY_WAIT_MS = 10_000L

/** Sends requests to the MangaDex API. Retries rate limits (429) and server errors a few times, honoring Retry-After. */
class MangaDexHttp(private val client: OkHttpClient) {
    suspend fun get(url: HttpUrl): String {
        var attempt = 0
        while (true) {
            try {
                return getOnce(url)
            } catch (e: RetryableException) {
                if (++attempt >= MAX_ATTEMPTS) {
                    throw IOException("MangaDex ${url.encodedPath} failed: HTTP ${e.code}")
                }
                delay(e.delayMs ?: (1_000L * attempt))
            }
        }
    }

    private suspend fun getOnce(url: HttpUrl): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", "dexter-android/0.1").build()
        client.newCall(request).execute().use { response ->
            if (response.code == 429 || response.code >= 500) {
                val wait = response.header("Retry-After")?.toLongOrNull()?.times(1_000)
                throw RetryableException(response.code, wait?.coerceAtMost(MAX_RETRY_WAIT_MS))
            }
            if (!response.isSuccessful) throw IOException("MangaDex ${url.encodedPath} failed: ${response.code}")
            response.body.string()
        }
    }

    private class RetryableException(val code: Int, val delayMs: Long?) : IOException()
}
