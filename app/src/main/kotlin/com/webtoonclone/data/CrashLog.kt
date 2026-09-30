package com.webtoonclone.data

import android.content.Context
import android.os.Build
import java.io.File
import java.time.Instant

private const val MAX_TRACE_LINES = 60

/** The text of a crash report. It holds the error, the app version, and the device model, and nothing else. */
fun formatCrashReport(error: Throwable, appVersion: String, device: String, timeMillis: Long): String = buildString {
    appendLine("Webtoon Clone crash report")
    appendLine("Time: ${Instant.ofEpochMilli(timeMillis)}")
    appendLine("App: $appVersion")
    appendLine("Device: $device")
    appendLine()
    append(error.stackTraceToString().lines().take(MAX_TRACE_LINES).joinToString("\n"))
}

/**
 * Keeps the last crash on the device so the app can offer to share it on the next launch. Nothing
 * is sent anywhere. Reports are saved only when [enabled] is true, which follows a setting that
 * is off until you turn it on.
 */
class CrashLog(private val context: Context) {
    @Volatile var enabled = false

    private val file get() = File(context.filesDir, "crash-report.txt")

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (enabled) runCatching { file.writeText(formatCrashReport(error, appVersion(), device(), System.currentTimeMillis())) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun pending(): String? = runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear() {
        runCatching { file.delete() }
    }

    private fun appVersion(): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "unknown"

    private fun device() = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
}
