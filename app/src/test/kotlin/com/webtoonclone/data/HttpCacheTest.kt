package com.webtoonclone.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HttpCacheTest {
    @Test
    fun apiResponsesAreReusableForAMinute() {
        assertEquals("public, max-age=60", cacheControlFor("/manga"))
        assertEquals("public, max-age=60", cacheControlFor("/manga/abc/feed"))
        assertEquals("public, max-age=60", cacheControlFor("/statistics/manga"))
    }

    @Test
    fun expiringImageServerUrlsAreNeverCached() {
        assertEquals("no-store", cacheControlFor("/at-home/server/abc"))
        assertEquals("no-store", cacheControlFor("/manga/random"))
    }
}
