package com.dexter.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchLineTest {
    @Test
    fun linesRoundTrip() {
        val line = watchLine(WearPaths.PROGRESS_REPLY, wearProgressPayload("Solo Leveling", "12", 3, 40))
        assertEquals(WearPaths.PROGRESS_REPLY to "Solo Leveling|12|3|40", parseWatchLine(line))
    }

    @Test
    fun tabsAndNewlinesInThePayloadCannotBreakTheLine() {
        val line = watchLine("/p", "a\tb\nc")
        assertEquals("/p" to "a b c", parseWatchLine(line))
    }

    @Test
    fun lineWithoutAPathIsIgnored() {
        assertNull(parseWatchLine("no tab here"))
        assertNull(parseWatchLine("\tpayload"))
    }
}
