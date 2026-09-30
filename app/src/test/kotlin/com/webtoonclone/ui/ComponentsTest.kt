package com.webtoonclone.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ComponentsTest {
    private val now = Instant.parse("2026-09-30T12:00:00Z")

    @Test
    fun timeAgoUsesTheLargestUnit() {
        assertEquals("just now", timeAgo("2026-09-30T12:00:00Z", now))
        assertEquals("5 min ago", timeAgo("2026-09-30T11:55:00Z", now))
        assertEquals("3 h ago", timeAgo("2026-09-30T09:00:00Z", now))
        assertEquals("2 d ago", timeAgo("2026-09-28T12:00:00Z", now))
        assertEquals("", timeAgo("not a date", now))
    }

    @Test
    fun compactShortensLargeCounts() {
        assertEquals("999", compact(999))
        assertEquals("1.2K", compact(1_234))
        assertEquals("2.5M", compact(2_500_000))
    }
}

class FriendlyErrorTest {
    private fun friendly(e: Throwable) = friendlyError(e, "fallback")

    @org.junit.Test
    fun offlineFailuresSaySoPlainly() {
        val offline = "No connection. Check your internet and try again."
        org.junit.Assert.assertEquals(offline, friendly(java.net.UnknownHostException("api.mangadex.org")))
        org.junit.Assert.assertEquals(offline, friendly(java.net.SocketTimeoutException("timeout")))
        org.junit.Assert.assertEquals(offline, friendly(java.net.ConnectException("refused")))
    }

    @org.junit.Test
    fun mangaDexErrorsAreTranslated() {
        org.junit.Assert.assertEquals(
            "MangaDex is busy right now. Try again in a moment.",
            friendly(java.io.IOException("MangaDex /manga failed: HTTP 429")),
        )
        org.junit.Assert.assertEquals(
            "MangaDex is having trouble. Try again in a moment.",
            friendly(java.io.IOException("MangaDex /manga failed: HTTP 503")),
        )
        org.junit.Assert.assertEquals(
            "MangaDex could not load this. Try again.",
            friendly(java.io.IOException("MangaDex /manga/x failed: 404")),
        )
    }

    @org.junit.Test
    fun otherFailuresUseTheFallback() {
        org.junit.Assert.assertEquals("fallback", friendly(IllegalStateException("boom")))
    }
}
