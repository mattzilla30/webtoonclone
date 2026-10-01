package com.dexter.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val AUTO_BACKUP_FILE = "dexter-backup.json"

/** Collects everything into a [Backup] and writes it to a file you chose, or to a folder on a schedule. */
class BackupService(
    private val context: Context,
    private val settings: SettingsStore,
    private val library: LibraryStore,
    private val progress: ProgressStore,
) {
    suspend fun create(): Backup = Backup(
        savedAt = System.currentTimeMillis(),
        library = library.current(),
        settings = settings.current(),
        progress = progress.export(),
    )

    suspend fun writeTo(uri: Uri) {
        val backup = create()
        withContext(Dispatchers.IO) {
            val text = encodeBackup(backup)
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
        }
    }

    /** Writes [AUTO_BACKUP_FILE] into the folder at [tree], replacing the file from the last run. */
    suspend fun writeToFolder(tree: Uri) {
        val backup = create()
        withContext(Dispatchers.IO) {
            val text = encodeBackup(backup)
            val resolver = context.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val existing = findChild(tree, parent, AUTO_BACKUP_FILE)
                ?: DocumentsContract.createDocument(resolver, parent, "application/json", AUTO_BACKUP_FILE)
                ?: error("Could not create the backup file")
            resolver.openOutputStream(existing, "wt")!!.use { it.write(text.toByteArray()) }
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

    /** Replaces everything on this device with [backup]. */
    suspend fun restore(backup: Backup) {
        library.replaceAll(backup.library)
        settings.update { backup.settings }
        progress.replaceAll(backup.progress)
    }
}
