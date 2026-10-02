package com.dexter.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

private val Context.nasDataStore by preferencesDataStore(name = "nas_shares")

private val sharesKey = stringPreferencesKey("shares")

/**
 * A bookmarked network share: a NAS folder you browse and stream from.
 *
 * LIMITATION: no SMB client library (such as smbj) ships with the app, so the `smb://` protocol
 * itself is not spoken here. Instead a share is opened through the Storage Access Framework: the
 * system file picker grants a persisted tree URI (your NAS mounted via the Files app, a
 * third-party SMB provider, or any document provider), and [NasTreeReader] streams CBZ files from
 * it. To add a direct SMB client later, implement [SmbClient] against the smbj API and plug it in
 * behind [NasShare.protocol].
 */
@Serializable
data class NasShare(
    val id: String,
    /** Display name, e.g. "Home NAS". */
    val name: String,
    /** "saf" for a Storage Access Framework tree, "smb" reserved for a future direct client. */
    val protocol: String = "saf",
    /** SMB host for display and for the future direct client, e.g. "192.168.1.10". */
    val host: String = "",
    /** Share name for the future direct client, e.g. "manga". */
    val share: String = "",
    /** Persisted SAF tree URI string, set when the user picks the folder. */
    val treeUri: String = "",
    val username: String = "",
)

/** Bookmarks for NAS shares, persisted as JSON in a small DataStore. Passwords are never stored. */
class NasShareStore(private val context: Context) {
    val shares: Flow<List<NasShare>> = context.nasDataStore.data.map { prefs ->
        decodeStored(ListSerializer(NasShare.serializer()), prefs[sharesKey]).orEmpty()
    }

    private suspend fun write(next: List<NasShare>) {
        context.nasDataStore.edit { it[sharesKey] = StoredJson.encodeToString(ListSerializer(NasShare.serializer()), next) }
    }

    suspend fun add(share: NasShare) {
        val current = shares.first()
        write(current.filterNot { it.id == share.id } + share)
    }

    suspend fun remove(id: String) {
        write(shares.first().filterNot { it.id == id })
    }

    suspend fun update(share: NasShare) = add(share)

    suspend fun get(id: String): NasShare? = shares.first().firstOrNull { it.id == id }

    /**
     * Attaches a persisted SAF tree [uri] to the share: takes a persistable read permission so the
     * bookmark survives restarts, then stores the URI string.
     */
    suspend fun attachTreeUri(id: String, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        get(id)?.let { update(it.copy(treeUri = uri.toString())) }
    }
}

/** One file or folder inside a NAS tree. */
data class NasEntry(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val mimeType: String? = null,
)

/**
 * Reads a NAS folder through a persisted Storage Access Framework tree URI: lists children and
 * streams files, so CBZ archives on the NAS open in the reader without copying them first.
 */
class NasTreeReader(private val context: Context) {
    /**
     * Lists the direct children of [treeUri], or of [dirUri] inside it. Folders sort before files,
     * both by name.
     */
    suspend fun list(treeUri: Uri, dirUri: Uri = treeUri): List<NasEntry> = withContext(Dispatchers.IO) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(dirUri))
        val entries = mutableListOf<NasEntry>()
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            null, null, null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idCol)
                val mime = cursor.getString(mimeCol)
                entries += NasEntry(
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                    name = cursor.getString(nameCol).orEmpty(),
                    isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                    size = cursor.getLong(sizeCol),
                    mimeType = mime,
                )
            }
        }
        entries.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    /** Opens [entry] for reading. The caller closes the stream. */
    suspend fun open(entry: NasEntry): java.io.InputStream = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(entry.uri)
            ?: throw java.io.IOException("Could not open ${entry.name}")
    }

    /** True for the archives the reader can open from a NAS tree. */
    fun isReadableArchive(entry: NasEntry): Boolean =
        !entry.isDirectory && entry.name.substringAfterLast('.', "").lowercase() in setOf("cbz", "zip")

    /**
     * Streams a NAS archive into the app cache so the ZIP page reader can open it, reusing the
     * cached copy when it is already there.
     */
    suspend fun cachedArchive(entry: NasEntry, cacheDir: File): File = withContext(Dispatchers.IO) {
        val dir = File(cacheDir, "nas").also { it.mkdirs() }
        val ext = entry.name.substringAfterLast('.', "cbz")
        val out = dir.resolve("${entry.uri.toString().hashCode().toString(16)}.$ext")
        if (!out.exists() || out.length() == 0L) {
            open(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
        }
        out
    }
}

/** Builds a SAF folder-pick intent; the result URI goes to [NasShareStore.attachTreeUri]. */
fun nasFolderPickIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
}
