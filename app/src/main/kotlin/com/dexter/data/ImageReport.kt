package com.dexter.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

private const val REPORT_URL = "https://api.mangadex.network/report"

/** One MangaDex@Home image load, in the shape MangaDex's report endpoint expects. */
@Serializable
data class ImageReport(
    val url: String,
    val success: Boolean,
    val bytes: Long,
    /** Milliseconds from request to response. */
    val duration: Long,
    /** True when the image server answered from its own cache (X-Cache: HIT). */
    val cached: Boolean,
)

/** MangaDex@Home image servers all live under mangadex.network. Covers and the API do not. */
fun isAtHomeHost(host: String): Boolean = host.endsWith(".mangadex.network")

/** The report for a finished request, or null when the host is not an image server. */
fun reportFor(
    request: Request,
    response: Response,
    durationMillis: Long,
    reportable: (String) -> Boolean = ::isAtHomeHost,
): ImageReport? {
    if (!reportable(request.url.host)) return null
    return ImageReport(
        url = request.url.toString(),
        success = response.isSuccessful,
        bytes = response.body.contentLength().coerceAtLeast(0),
        duration = durationMillis,
        cached = response.header("X-Cache")?.startsWith("HIT", ignoreCase = true) == true,
    )
}

/** A report for a request that failed before any response came back. */
fun failureReportFor(
    request: Request,
    durationMillis: Long,
    reportable: (String) -> Boolean = ::isAtHomeHost,
): ImageReport? =
    if (!reportable(request.url.host)) null
    else ImageReport(request.url.toString(), success = false, bytes = 0, duration = durationMillis, cached = false)

/** Sends reports without waiting for an answer. A failed report is dropped. */
class ImageReporter(private val client: OkHttpClient = OkHttpClient()) {
    private val json = Json

    fun send(report: ImageReport) {
        val body = json.encodeToString(ImageReport.serializer(), report).toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(REPORT_URL).header("User-Agent", "dexter-android/0.1").post(body).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit
            override fun onResponse(call: Call, response: Response) = response.close()
        })
    }
}

/** Reports page image loads from MangaDex@Home servers, when [enabled] says so. */
class ImageReportInterceptor(
    private val send: (ImageReport) -> Unit,
    private val enabled: () -> Boolean,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!enabled() || !isAtHomeHost(request.url.host)) return chain.proceed(request)
        val start = System.nanoTime()
        fun elapsed() = (System.nanoTime() - start) / 1_000_000
        try {
            val response = chain.proceed(request)
            reportFor(request, response, elapsed())?.let(send)
            return response
        } catch (e: IOException) {
            // A load the app dropped on purpose, such as a prefetch for a chapter you left, is not a server failure.
            if (!chain.call().isCanceled()) failureReportFor(request, elapsed())?.let(send)
            throw e
        }
    }
}
