package com.dexter.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSource
import java.io.IOException
import kotlin.coroutines.resumeWithException

private const val MAX_ATTEMPTS = 3
private const val MAX_RETRY_WAIT_MS = 10_000L

/** Sends requests to the MangaDex API. Retries rate limits (429) and server errors a few times, honoring Retry-After. */
class MangaDexHttp(private val client: OkHttpClient) {
    /** The body of [url] as text. */
    suspend fun get(url: HttpUrl): String = get(url) { it.readUtf8() }

    /**
     * Reads the body of [url] with [read] as it arrives, on OkHttp's thread. Decoding JSON this way skips
     * holding the whole reply as one string first, which for a 500-chapter feed is most of a megabyte.
     */
    suspend fun <T> get(url: HttpUrl, read: (BufferedSource) -> T): T = send(Request.Builder().url(url).build(), read)

    /** Sends [request], such as a signed-in one with a body, with the same retries as [get]. */
    suspend fun <T> send(request: Request, read: (BufferedSource) -> T): T {
        var attempt = 0
        while (true) {
            try {
                return sendOnce(request, read)
            } catch (e: RetryableException) {
                if (++attempt >= MAX_ATTEMPTS) {
                    throw HttpStatusException(e.code, "MangaDex ${request.url.encodedPath} failed: HTTP ${e.code}")
                }
                delay(e.delayMs ?: (1_000L * attempt))
            }
        }
    }

    /** One request. Cancelling the coroutine cancels the call, so a screen that goes away stops its downloads. */
    private suspend fun <T> sendOnce(original: Request, read: (BufferedSource) -> T): T = suspendCancellableCoroutine { continuation ->
        val request = original.newBuilder().header("User-Agent", "dexter-android/0.1").build()
        val url = request.url
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resumeWith(runCatching { response.use { read(checked(url, it).body.source()) } })
                }
            },
        )
    }

    private fun checked(url: HttpUrl, response: Response): Response {
        if (response.code == 429 || response.code >= 500) {
            val wait = response.header("Retry-After")?.toLongOrNull()?.times(1_000)
            throw RetryableException(response.code, wait?.coerceAtMost(MAX_RETRY_WAIT_MS))
        }
        if (!response.isSuccessful) throw HttpStatusException(response.code, "MangaDex ${url.encodedPath} failed: ${response.code}")
        return response
    }

    private class RetryableException(val code: Int, val delayMs: Long?) : IOException()
}

/** MangaDex answered with an error status, such as 404 for a series that was removed. */
class HttpStatusException(val code: Int, message: String) : IOException(message)

/**
 * Whether a background job should try again after [error]. A dropped connection, a rate limit, or a
 * server error may pass. A 404 or another client error will not, and neither will a reply that does
 * not parse.
 */
fun isWorthRetrying(error: Throwable): Boolean =
    error is IOException && (error !is HttpStatusException || error.code == 429 || error.code >= 500)
