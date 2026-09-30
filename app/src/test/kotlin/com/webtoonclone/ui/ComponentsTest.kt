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
