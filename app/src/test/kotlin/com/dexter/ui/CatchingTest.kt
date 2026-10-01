package com.dexter.ui

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CatchingTest {
    @Test
    fun returnsTheValue() {
        assertEquals(5, catching { 5 }.getOrNull())
    }

    @Test
    fun turnsAFailureIntoAResult() {
        val result = catching<Int> { throw IOException("down") }
        assertTrue(result.isFailure)
        assertEquals("down", result.exceptionOrNull()?.message)
    }

    @Test
    fun letsCancellationThrough() {
        assertThrows(CancellationException::class.java) { catching<Int> { throw CancellationException("stop") } }
    }
}
