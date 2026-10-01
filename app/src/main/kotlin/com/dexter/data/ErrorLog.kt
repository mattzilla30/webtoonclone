package com.dexter.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.concurrent.Executors

/** One failure the app recorded: when, what kind, and the first lines of its stack trace. */
@Serializable
data class ErrorEntry(val at: Long, val kind: String, val message: String, val trace: String)

private const val MAX_ENTRIES = 200
private const val TRACE_LINES = 12

/** The first [lines] lines of [error]'s stack trace, and of its causes', as text. */
fun shortTrace(error: Throwable, lines: Int = TRACE_LINES): String = error.stackTraceToString().lineSequence().take(lines).joinToString("\n")

/** [entries] with [entry] added at the front, keeping the newest [max]. */
fun withEntry(entries: List<ErrorEntry>, entry: ErrorEntry, max: Int = MAX_ENTRIES): List<ErrorEntry> = (listOf(entry) + entries).take(max)

/**
 * The last two hundred failures, kept in a file so you can read them in Settings without a computer.
 * Writes happen on one background thread, except a crash, which is written before the app closes.
 */
object ErrorLog {
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "error-log").apply { isDaemon = true } }
    private val serializer = ListSerializer(ErrorEntry.serializer())
    private var file: File? = null
    private val _entries = MutableStateFlow<List<ErrorEntry>>(emptyList())
    val entries: StateFlow<List<ErrorEntry>> = _entries

    /** Loads the saved log and starts recording crashes. Called once when the app starts. */
    fun init(context: Context) {
        val target = File(context.filesDir, "errors.json")
        file = target
        writer.execute { _entries.value = runCatching { StoredJson.decodeFromString(serializer, target.readText()) }.getOrDefault(emptyList()) }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { save(withEntry(_entries.value, entry("Crash", error))) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun entry(kind: String, error: Throwable) =
        ErrorEntry(System.currentTimeMillis(), kind, "${error::class.java.simpleName}: ${error.message.orEmpty()}".take(500), shortTrace(error))

    private fun save(entries: List<ErrorEntry>) {
        _entries.value = entries
        file?.writeText(StoredJson.encodeToString(serializer, entries))
    }

    /** Records [error] under [kind], such as "Background task" or "Shown to you". */
    fun record(kind: String, error: Throwable) {
        val entry = entry(kind, error)
        writer.execute { runCatching { save(withEntry(_entries.value, entry)) } }
    }

    fun clear() {
        writer.execute { runCatching { save(emptyList()) } }
    }
}

/** The log as plain text, newest first, for copying or sharing. */
fun errorLogText(entries: List<ErrorEntry>, format: (Long) -> String): String = entries.joinToString("\n\n") { entry ->
    "${format(entry.at)}  ${entry.kind}\n${entry.message}\n${entry.trace}"
}
