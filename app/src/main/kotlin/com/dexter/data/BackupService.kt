package com.dexter.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.dexter.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

const val AUTO_BACKUP_FILE = "dexter-backup.json"

/** Collects everything into a [Backup] and writes it to a file you chose, or to a folder on a schedule. */
class BackupService(
    private val context: Context,
    private val settings: SettingsStore,
    private val library: LibraryStore,
    private val progress: ProgressStore,
    private val db: AppDatabase,
    private val downloads: DownloadStore,
    private val readingLists: ReadingListStore,
    private val smartLists: SmartListStore,
    private val blacklist: ChapterBlacklist,
) {
    suspend fun create(): Backup = Backup(
        savedAt = System.currentTimeMillis(),
        library = library.current(),
        settings = settings.current(),
        progress = progress.export(),
        history = db.stats().observe().first().map { it.toBackup() },
        downloads = downloads.saved.first().map { it.toBackup() },
        queue = db.queue().observe().first().map { it.toBackup() },
        readingLists = readingLists.all.first(),
        smartLists = smartLists.all.first(),
        blacklist = blacklist.all().first(),
    )

    suspend fun writeTo(uri: Uri) {
        val backup = create()
        withContext(Dispatchers.IO) {
            val text = encodeBackup(backup)
            // Stage the complete backup in a temp file first, so an encoding or disk failure
            // can never truncate the destination into a half-written backup.
            val tmp = File(context.cacheDir, "backup.tmp")
            try {
                tmp.writeAtomically { it.write(text.toByteArray()) }
                context.contentResolver.openOutputStream(uri, "wt")!!.use { out ->
                    tmp.inputStream().use { it.copyTo(out) }
                }
            } finally {
                tmp.delete()
            }
        }
    }

    /** Writes [AUTO_BACKUP_FILE] into the folder at [tree], replacing the file from the last run. */
    suspend fun writeToFolder(tree: Uri) {
        val backup = create()
        withContext(Dispatchers.IO) {
            val text = encodeBackup(backup)
            val resolver = context.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            // Write the new backup to a temp document first: the previous backup stays intact
            // until the new one is fully written, so a crash can never destroy the last good backup.
            val tmpName = "$AUTO_BACKUP_FILE.tmp"
            findChild(tree, parent, tmpName)?.let { resolver.delete(it, null, null) }
            val tmp = DocumentsContract.createDocument(resolver, parent, "application/json", tmpName)
                ?: error("Could not create the backup file")
            try {
                resolver.openOutputStream(tmp, "wt")!!.use { it.write(text.toByteArray()) }
                findChild(tree, parent, AUTO_BACKUP_FILE)?.let { resolver.delete(it, null, null) }
                val renamed = runCatching { DocumentsContract.renameDocument(resolver, tmp, AUTO_BACKUP_FILE) }.getOrNull()
                if (renamed == null) {
                    // This provider cannot rename documents: fall back to overwriting the old file
                    // with the complete new bytes. The old backup is only truncated once the new
                    // content fully exists in the temp document.
                    val existing = findChild(tree, parent, AUTO_BACKUP_FILE)
                        ?: DocumentsContract.createDocument(resolver, parent, "application/json", AUTO_BACKUP_FILE)
                        ?: error("Could not create the backup file")
                    resolver.openOutputStream(existing, "wt")!!.use { out ->
                        resolver.openInputStream(tmp)!!.use { it.copyTo(out) }
                    }
                    resolver.delete(tmp, null, null)
                }
            } catch (e: Exception) {
                runCatching { resolver.delete(tmp, null, null) }
                throw e
            }
        }
    }

    private fun findChild(tree: Uri, parent: Uri, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        context.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name) return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
            }
        }
        return null
    }

    /**
     * Replaces everything on this device with [backup]. A snapshot of the current state is taken
     * first: if a later step throws, the snapshot is put back on a best-effort basis instead of
     * leaving a half-restored device.
     */
    suspend fun restore(backup: Backup) {
        val snapshot = runCatching { create() }.getOrNull()
        try {
            applyBackup(backup)
        } catch (e: Exception) {
            if (snapshot != null) runCatching { applyBackup(snapshot) }
            throw e
        }
    }

    private suspend fun applyBackup(backup: Backup) {
        library.replaceAll(backup.library)
        // A backup comes from a phone that was already set up, so setup does not ask again.
        settings.update { backup.settings.copy(setupDone = true) }
        progress.replaceAll(backup.progress)
        // Reading history and time, replacing what is here.
        db.stats().clear()
        backup.history.forEach { db.stats().insertIfAbsent(it.toEntity()) }
        // Downloaded chapters: the rows whose page files are on the device stay, the rest drop out.
        // A zip archive restores the files before this; a plain JSON backup on the same device already has them.
        backup.downloads.forEach { downloads.restoreRow(it) }
        downloads.prune()
        // The queue, replacing what is waiting here.
        db.queue().deleteAll()
        backup.queue.forEach { db.queue().insert(it.toEntity()) }
        // Reading lists, smart lists, and the chapter blacklist, replacing what is here.
        readingLists.replaceAll(backup.readingLists)
        smartLists.replaceAll(backup.smartLists)
        blacklist.replaceAll(backup.blacklist)
    }
}
