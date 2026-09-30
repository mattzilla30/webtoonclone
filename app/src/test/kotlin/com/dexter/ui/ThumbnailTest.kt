package com.dexter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThumbnailTest {
    @Test
    fun coverAddressesShrink() {
        assertEquals("https://uploads.mangadex.org/covers/a/b.jpg.256.jpg", thumbnailUrl("https://uploads.mangadex.org/covers/a/b.jpg.512.jpg"))
    }

    @Test
    fun otherAddressesPassThrough() {
        assertEquals("file:///x/001.jpg", thumbnailUrl("file:///x/001.jpg"))
        assertNull(thumbnailUrl(null))
    }
}
