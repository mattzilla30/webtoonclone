package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailsExtrasTest {
    @Test
    fun relationKindsReadNicely() {
        assertEquals("Spin-off", relationLabel("spin_off"))
        assertEquals("Sequel", relationLabel("sequel"))
        assertEquals("Some new kind", relationLabel("some_new_kind"))
    }

    @Test
    fun ratingCountsKeepScoresOneToTenHighestFirst() {
        val counts = ratingCounts(mapOf("1" to 5, "10" to 40, "7" to 12, "0" to 9, "x" to 1))
        assertEquals(listOf(10, 7, 1), counts.keys.toList())
        assertEquals(40, counts[10])
    }
}
