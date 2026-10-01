package com.dexter.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * JSON for what the app keeps on the device. Unknown fields are skipped, and a value that no longer
 * fits its field, such as an option name from another version, falls back to the field's default. One
 * odd field then costs that field, not the whole file.
 */
val StoredJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/** The value stored as [raw], or null when nothing is stored or the text cannot be read. */
fun <T> decodeStored(serializer: KSerializer<T>, raw: String?): T? =
    raw?.let { runCatching { StoredJson.decodeFromString(serializer, it) }.getOrNull() }

/**
 * Whether [raw] is stored text that cannot be read. A save would replace it with defaults, so the
 * stores copy it aside first and the data can still be recovered.
 */
fun <T> isUnreadable(serializer: KSerializer<T>, raw: String?): Boolean = raw != null && decodeStored(serializer, raw) == null
