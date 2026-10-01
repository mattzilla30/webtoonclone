package com.dexter.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val BACKUP_VERSION = 1

/** Everything kept on the device, in one file you can save and restore later. */
@Serializable
data class Backup(
    val version: Int = BACKUP_VERSION,
    val savedAt: Long = 0L,
    val library: LibraryData = LibraryData(),
    val settings: Settings = Settings(),
    /** Last chapter and page per series, as "chapterId:page". */
    val progress: Map<String, String> = emptyMap(),
)

private val backupJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    // A backup from another version may name an option this one does not know. That field takes its default.
    coerceInputValues = true
}

fun encodeBackup(backup: Backup): String = backupJson.encodeToString(Backup.serializer(), backup)

/** Reads a backup file. Returns null when the text is not a backup, or was made by a newer app. */
fun decodeBackup(text: String): Backup? {
    val backup = runCatching { backupJson.decodeFromString(Backup.serializer(), text) }.getOrNull() ?: return null
    return backup.takeIf { it.version in 1..BACKUP_VERSION }
}
