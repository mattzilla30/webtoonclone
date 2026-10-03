package com.dexter.platform

/**
 * Watch-link message paths shared by the phone app and the wear module. Both sides use
 * these exact strings; keep them in sync with `wear/src/main/kotlin/com/dexter/wear/`.
 */
object WearPaths {
    /** Wear -> phone: turn one page backward in the open reader. */
    const val PAGE_PREVIOUS = "/dexter/page/previous"

    /** Wear -> phone: turn one page forward in the open reader. */
    const val PAGE_NEXT = "/dexter/page/next"

    /** Wear -> phone: ask what is currently being read. */
    const val PROGRESS_REQUEST = "/dexter/progress/request"

    /**
     * Phone -> wear: the current reading state, as `seriesTitle|chapterNumber|page|total`.
     * Empty payload means nothing is being read. `|` and `\` inside values are backslash-escaped.
     */
    const val PROGRESS_REPLY = "/dexter/progress"
}

/** Packs a progress reply payload for [WearPaths.PROGRESS_REPLY]. */
fun wearProgressPayload(seriesTitle: String, chapterNumber: String, page: Int, total: Int): String =
    listOf(seriesTitle, chapterNumber, page.toString(), total.toString())
        .joinToString("|") { it.replace("\\", "\\\\").replace("|", "\\|") }

/**
 * Splits a payload on `|` that is not backslash-escaped, unescaping `\|` and `\\`.
 * Duplicated as `splitEscaped` in the wear module's `parseWatchProgress`; keep the two in sync.
 */
fun splitEscapedPayload(payload: String): List<String> {
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

/** Unpacks a [WearPaths.PROGRESS_REPLY] payload. Null when the payload is empty or malformed. */
fun parseWearProgress(payload: String): WearProgress? {
    if (payload.isBlank()) return null
    val parts = splitEscapedPayload(payload)
    if (parts.size != 4) return null
    return WearProgress(
        seriesTitle = parts[0],
        chapterNumber = parts[1],
        page = parts[2].toIntOrNull() ?: return null,
        total = parts[3].toIntOrNull() ?: return null,
    )
}

/** What the watch shows at a glance. */
data class WearProgress(
    val seriesTitle: String,
    val chapterNumber: String,
    val page: Int,
    val total: Int,
) {
    /** How far through the chapter the reader is, 0 to 1. */
    val share: Float get() = if (total > 0) (page + 1f) / total else 0f
}

/** The Bluetooth service the phone listens on and the watch connects to. Both sides use this exact id. */
val WATCH_LINK_UUID: java.util.UUID = java.util.UUID.fromString("6f3c1a52-8d0e-4c8b-9a51-2f7d1e9b4c10")

/** One message on the watch link: the path, a tab, the payload, and a newline. Tabs and newlines in the payload become spaces. */
fun watchLine(path: String, payload: String = ""): String = path + "\t" + payload.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ') + "\n"

/** The path and payload of one watch-link line, or null when it is not one. */
fun parseWatchLine(line: String): Pair<String, String>? {
    val tab = line.indexOf('\t')
    if (tab <= 0) return null
    return line.substring(0, tab) to line.substring(tab + 1).trimEnd('\n', '\r')
}
