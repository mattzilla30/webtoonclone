package com.dexter.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resumeWithException

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

    /** One request. Cancelling the coroutine cancels the call, so a screen that goes away stops its downloads. */
    private suspend fun getOnce(url: HttpUrl): String = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url).header("User-Agent", "dexter-android/0.1").build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resumeWith(runCatching { response.use { read(url, it) } })
                }
            },
        )
    }

    private fun read(url: HttpUrl, response: Response): String {
        if (response.code == 429 || response.code >= 500) {
            val wait = response.header("Retry-After")?.toLongOrNull()?.times(1_000)
            throw RetryableException(response.code, wait?.coerceAtMost(MAX_RETRY_WAIT_MS))
        }
        if (!response.isSuccessful) throw IOException("MangaDex ${url.encodedPath} failed: ${response.code}")
        return response.body.string()
    }

    private class RetryableException(val code: Int, val delayMs: Long?) : IOException()
}
