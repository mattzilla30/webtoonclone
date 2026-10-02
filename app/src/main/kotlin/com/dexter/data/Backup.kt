package com.dexter.data

import com.dexter.data.db.DownloadEntity
import com.dexter.data.db.QueuedDownloadEntity
import com.dexter.data.db.ReadEventEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val BACKUP_VERSION = 2

/** One row of the reading history: when a chapter was opened and how long it was read. */
@Serializable
data class ReadEvent(
    val chapterId: String,
    val seriesId: String,
    val seriesTitle: String,
    val at: Long,
    val durationMs: Long = 0,
    val genre: String? = null,
)

/** A downloaded chapter's row. Its page files travel in the zip archive, not in this JSON. */
@Serializable
data class SavedDownload(
    val chapterId: String,
    val seriesId: String,
    val seriesTitle: String,
    val coverUrl: String?,
    val number: String,
    val title: String,
    val volume: String?,
    val groupName: String?,
    val publishedAt: String,
    val pageCount: Int,
    val bytes: Long,
    val savedAt: Long,
)

fun ReadEventEntity.toBackup(): ReadEvent = ReadEvent(chapterId, seriesId, seriesTitle, at, durationMs, genre)

fun ReadEvent.toEntity(): ReadEventEntity = ReadEventEntity(chapterId, seriesId, seriesTitle, at, durationMs, genre)

fun DownloadEntity.toBackup(): SavedDownload =
    SavedDownload(chapterId, seriesId, seriesTitle, coverUrl, number, title, volume, groupName, publishedAt, pageCount, bytes, savedAt)

fun SavedDownload.toEntity(): DownloadEntity =
    DownloadEntity(chapterId, seriesId, seriesTitle, coverUrl, number, title, volume, groupName, publishedAt, pageCount, bytes, savedAt)

/** A chapter waiting in the download queue. */
@Serializable
data class QueuedDownload(
    val chapterId: String,
    val seriesId: String,
    val seriesTitle: String,
    val coverUrl: String?,
    val number: String,
    val title: String,
    val volume: String?,
    val groupName: String?,
    val publishedAt: String,
    val position: Long,
)

fun QueuedDownloadEntity.toBackup(): QueuedDownload =
    QueuedDownload(chapterId, seriesId, seriesTitle, coverUrl, number, title, volume, groupName, publishedAt, position)

fun QueuedDownload.toEntity(): QueuedDownloadEntity =
    QueuedDownloadEntity(chapterId, seriesId, seriesTitle, coverUrl, number, title, volume, groupName, publishedAt, position)

/** Everything kept on the device, in one file you can save and restore later. */
@Serializable
data class Backup(
    val version: Int = BACKUP_VERSION,
    val savedAt: Long = 0L,
    val library: LibraryData = LibraryData(),
    val settings: Settings = Settings(),
    /** Last chapter and page per series, as "chapterId:page". */
    val progress: Map<String, String> = emptyMap(),
    /** Reading history and reading time, as [StatsStore] records them. */
    val history: List<ReadEvent> = emptyList(),
    /**
     * Downloaded chapters. Their page files travel in the zip archive; restoring a plain JSON backup
     * keeps the rows whose files are on the device and drops the rest.
     */
    val downloads: List<SavedDownload> = emptyList(),
    /** Chapters waiting in the download queue, in queue order. */
    val queue: List<QueuedDownload> = emptyList(),
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
