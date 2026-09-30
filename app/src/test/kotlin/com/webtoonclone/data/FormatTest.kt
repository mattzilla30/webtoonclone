package com.webtoonclone.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun sizesUseTheLargestFittingUnit() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1023 B", formatBytes(1023))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("5.0 MB", formatBytes(5L * 1024 * 1024))
        assertEquals("2.3 GB", formatBytes((2.25 * 1024 * 1024 * 1024).toLong()))
    }
}
