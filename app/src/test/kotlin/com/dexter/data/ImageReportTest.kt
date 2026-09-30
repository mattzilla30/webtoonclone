package com.dexter.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageReportTest {
    private fun request(url: String) = Request.Builder().url(url).build()

    private fun response(req: Request, code: Int, body: String, xCache: String? = null) =
        Response.Builder()
            .request(req).protocol(Protocol.HTTP_1_1).code(code).message("m")
            .apply { if (xCache != null) header("X-Cache", xCache) }
            .body(body.toResponseBody("image/jpeg".toMediaType()))
            .build()

    @Test
    fun onlyImageServersAreReported() {
        assertTrue(isAtHomeHost("abc.mangadex.network"))
        assertFalse(isAtHomeHost("uploads.mangadex.org"))
        assertFalse(isAtHomeHost("api.mangadex.org"))
    }

    @Test
    fun aCacheHitIsReportedAsCached() {
        val req = request("https://abc.mangadex.network/data/h/1.jpg")
        val report = reportFor(req, response(req, 200, "12345", xCache = "HIT"), durationMillis = 80)!!
        assertEquals(ImageReport(req.url.toString(), success = true, bytes = 5, duration = 80, cached = true), report)
    }

    @Test
    fun aMissAndAnErrorAreReportedHonestly() {
        val req = request("https://abc.mangadex.network/data/h/1.jpg")
        assertFalse(reportFor(req, response(req, 200, "x", xCache = "MISS"), 10)!!.cached)
        assertFalse(reportFor(req, response(req, 404, ""), 10)!!.success)
    }

    @Test
    fun otherHostsProduceNoReport() {
        val req = request("https://uploads.mangadex.org/covers/x.jpg")
        assertNull(reportFor(req, response(req, 200, "x"), 10))
        assertNull(failureReportFor(req, 10))
    }

    @Test
    fun aNetworkFailureIsReportedAsUnsuccessful() {
        val req = request("https://abc.mangadex.network/data/h/1.jpg")
        assertFalse(failureReportFor(req, 500)!!.success)
    }

    @Test
    fun theJsonBodyUsesTheFieldNamesMangaDexExpects() {
        val json = Json.encodeToString(ImageReport.serializer(), ImageReport("https://x/y.jpg", true, 42, 7, false))
        val obj = Json.parseToJsonElement(json).jsonObject
        assertEquals(setOf("url", "success", "bytes", "duration", "cached"), obj.keys)
        assertEquals("https://x/y.jpg", obj["url"]!!.jsonPrimitive.content)
        assertTrue(obj["success"]!!.jsonPrimitive.boolean)
        assertEquals(42L, obj["bytes"]!!.jsonPrimitive.long)
    }
}
