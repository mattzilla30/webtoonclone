package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockingTest {
    @Test
    fun blockedTagsJoinTheExclusions() {
        assertEquals(listOf("Gore", "Harem"), effectiveExcluded(listOf("Gore"), setOf("Harem"), emptyList()))
    }

    @Test
    fun aTagYouSearchForIsNotBlocked() {
        assertEquals(emptyList<String>(), effectiveExcluded(emptyList(), setOf("Harem"), listOf("harem")))
    }

    @Test
    fun duplicatesCollapseIgnoringCase() {
        assertEquals(listOf("Gore"), effectiveExcluded(listOf("Gore"), setOf("gore"), emptyList()))
    }
}
