package com.dexter.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class MangaDexHttpTest {
    private fun client(codes: List<Int>, calls: MutableList<Int>): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            Interceptor { chain ->
                val code = codes[minOf(calls.size, codes.lastIndex)]
                calls += code
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("x")
                    .header("Retry-After", "0")
                    .body("""{"ok":true}""".toResponseBody())
                    .build()
            },
        )
        .build()

    private val url = "https://api.mangadex.org/manga".toHttpUrl()

    @Test
    fun retriesARateLimitThenSucceeds() = runBlocking {
        val calls = mutableListOf<Int>()
        assertEquals("""{"ok":true}""", MangaDexHttp(client(listOf(429, 200), calls)).get(url))
        assertEquals(listOf(429, 200), calls)
    }

    @Test
    fun givesUpAfterThreeTries() {
        val calls = mutableListOf<Int>()
        assertThrows(IOException::class.java) { runBlocking { MangaDexHttp(client(listOf(503), calls)).get(url) } }
        assertEquals(3, calls.size)
    }

    @Test
    fun clientErrorsAreNotRetried() {
        val calls = mutableListOf<Int>()
        assertThrows(IOException::class.java) { runBlocking { MangaDexHttp(client(listOf(404), calls)).get(url) } }
        assertEquals(1, calls.size)
    }

    @Test
    fun cancellingTheCoroutineCancelsTheRequest() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val aborted = CompletableDeferred<Boolean>()
        val blocking = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    entered.complete(Unit)
                    // Hold the request until it is cancelled, as a slow server would.
                    val deadline = System.currentTimeMillis() + 5_000
                    while (!chain.call().isCanceled() && System.currentTimeMillis() < deadline) Thread.sleep(10)
                    aborted.complete(chain.call().isCanceled())
                    throw IOException("Canceled")
                },
            )
            .build()
        val job = launch(Dispatchers.Default) { MangaDexHttp(blocking).get(url) }
        entered.await()
        job.cancelAndJoin()
        assertEquals(true, aborted.await())
    }
}
