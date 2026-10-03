package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PinLockoutTest {
    @Test
    fun firstTriesAreFreeThenTheWaitDoublesToAnHour() {
        assertEquals(0L, pinLockoutMs(4))
        assertEquals(30_000L, pinLockoutMs(5))
        assertEquals(60_000L, pinLockoutMs(6))
        assertEquals(60 * 60_000L, pinLockoutMs(40))
    }
}
