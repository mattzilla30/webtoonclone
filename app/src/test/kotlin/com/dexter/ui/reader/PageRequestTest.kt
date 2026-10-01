package com.dexter.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class PageRequestTest {
    @Test
    fun aPageKeepsItsKeyAcrossServers() {
        val first = pageCacheKey("https://abc.mangadex.network:443/token1/data/hash/x1.png")
        val second = pageCacheKey("https://xyz.mangadex.network/token2/data/hash/x1.png")
        assertEquals("/data/hash/x1.png", first)
        assertEquals(first, second)
    }

    @Test
    fun saverPagesKeepTheirOwnKey() {
        assertEquals("/data-saver/hash/x1.jpg", pageCacheKey("https://abc.mangadex.network/t/data-saver/hash/x1.jpg"))
    }

    @Test
    fun otherAddressesStayWhole() {
        assertEquals("file:///data/user/0/com.dexter/files/downloads/c/001.png", pageCacheKey("file:///data/user/0/com.dexter/files/downloads/c/001.png"))
        assertEquals("https://example.com/page.png", pageCacheKey("https://example.com/page.png"))
    }
}
