package com.webtoonclone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLogTest {
    private fun deep(n: Int): Throwable = if (n == 0) IllegalStateException("boom") else deep(n - 1)

    @Test
    fun theReportNamesTheErrorTheVersionAndTheDevice() {
        val report = formatCrashReport(IllegalStateException("boom"), "0.1.0", "Pixel 9, Android 17 (API 37)", 0L)
        assertTrue(report.contains("Webtoon Clone crash report"))
        assertTrue(report.contains("App: 0.1.0"))
        assertTrue(report.contains("Device: Pixel 9, Android 17 (API 37)"))
        assertTrue(report.contains("java.lang.IllegalStateException: boom"))
        assertTrue(report.contains("Time: 1970-01-01T00:00:00Z"))
    }

    @Test
    fun aLongTraceIsCutToSixtyLines() {
        val report = formatCrashReport(deep(200), "1", "d", 0L)
        val traceLines = report.lines().drop(5)
        assertEquals(60, traceLines.size)
    }
}
