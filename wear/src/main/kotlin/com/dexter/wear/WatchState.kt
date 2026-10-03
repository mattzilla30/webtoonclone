package com.dexter.wear

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the watch shows at a glance: the chapter open on the phone and how far through it the
 * reader is. Mirrors the phone's `com.dexter.platform.WearProgress`; the payload format
 * (`seriesTitle|chapterNumber|page|total`) is the same one the phone packs with its
 * `wearProgressPayload` helper, parsed here so the wear module keeps building standalone.
 */
data class WatchProgress(
    val seriesTitle: String,
    val chapterNumber: String,
    val page: Int,
    val total: Int,
) {
    /** How far through the chapter the reader is, 0 to 1. */
    val share: Float get() = if (total > 0) (page + 1f) / total else 0f
}

/**
 * Splits a payload on `|` that is not backslash-escaped, unescaping `\|` and `\\`.
 * Mirrors the phone's `splitEscapedPayload`; keep the two in sync.
 */
private fun splitEscaped(payload: String): List<String> {
    val parts = ArrayList<String>()
    val current = StringBuilder()
    var i = 0
    while (i < payload.length) {
        val c = payload[i]
        if (c == '\\' && i + 1 < payload.length && (payload[i + 1] == '|' || payload[i + 1] == '\\')) {
            current.append(payload[i + 1])
            i += 2
        } else if (c == '|') {
            parts += current.toString()
            current.clear()
            i++
        } else {
            current.append(c)
            i++
        }
    }
    parts += current.toString()
    return parts
}

/** Parses a `seriesTitle|chapterNumber|page|total` payload. Null when empty or malformed. */
fun parseWatchProgress(payload: String): WatchProgress? {
    if (payload.isBlank()) return null
    val parts = splitEscaped(payload)
    if (parts.size != 4) return null
    return WatchProgress(
        seriesTitle = parts[0],
        chapterNumber = parts[1],
        page = parts[2].toIntOrNull() ?: return null,
        total = parts[3].toIntOrNull() ?: return null,
    )
}

/**
 * The watch's latest known reading state. [PhoneLink] writes it when the phone's
 * reply arrives; the activity reads it. Null means nothing is being read (the phone sent an
 * empty payload, or nothing has arrived yet).
 */
object WatchState {
    private val _progress = MutableStateFlow<WatchProgress?>(null)
    val progress: StateFlow<WatchProgress?> = _progress.asStateFlow()

    fun onProgressReply(payload: ByteArray) {
        _progress.value = parseWatchProgress(String(payload, Charsets.UTF_8))
    }
}
