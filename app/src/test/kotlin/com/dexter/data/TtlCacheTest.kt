package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtlCacheTest {
    private var now = 1_000L
    private val cache = TtlCache<String, String>(ttlMillis = 100, clock = { now })

    @Test
    fun entriesAreReturnedUntilTheyExpire() {
        cache.put("a", "one")
        now += 99
        assertEquals("one", cache.get("a"))
        now += 1
        assertNull(cache.get("a"))
    }

    @Test
    fun aNewValueRestartsTheClock() {
        cache.put("a", "one")
        now += 90
        cache.put("a", "two")
        now += 90
        assertEquals("two", cache.get("a"))
    }

    @Test
    fun removedAndUnknownKeysReturnNothing() {
        cache.put("a", "one")
        cache.remove("a")
        assertNull(cache.get("a"))
        assertNull(cache.get("never"))
    }
}
