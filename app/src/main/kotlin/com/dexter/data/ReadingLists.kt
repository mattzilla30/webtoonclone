package com.dexter.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

private val Context.readingListDataStore by preferencesDataStore(name = "reading_lists")

private val readingListsKey = stringPreferencesKey("reading_lists")

/** The built-in "Want to read" pile: an inbox for series to try later. */
const val WANT_TO_READ_LIST_ID = "want-to-read"

/** One series on a reading list, in list order. */
@Serializable
data class ReadingListEntry(
    val seriesId: String,
    val title: String,
    val coverUrl: String? = null,
    val addedAt: Long = 0,
)

/**
 * A curated, ordered cross-series list ("Beginner horror manga"), shareable as text or JSON.
 * Unlike [com.dexter.data.LibraryData.collections], entries keep an explicit order you can
 * rearrange, and a list carries a description for sharing.
 */
@Serializable
data class ReadingList(
    val id: String,
    val name: String,
    val description: String = "",
    val entries: List<ReadingListEntry> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** Ordered reading lists, persisted as JSON. */
class ReadingListStore(private val context: Context) {
    val all: Flow<List<ReadingList>> = context.readingListDataStore.data.map { prefs ->
        decodeStored(ListSerializer(ReadingList.serializer()), prefs[readingListsKey]).orEmpty()
    }

    private suspend fun write(next: List<ReadingList>) {
        context.readingListDataStore.edit {
            it[readingListsKey] = StoredJson.encodeToString(ListSerializer(ReadingList.serializer()), next)
        }
    }

    private suspend fun update(id: String, change: (ReadingList) -> ReadingList) {
        write(all.first().map { if (it.id == id) change(it).copy(updatedAt = System.currentTimeMillis()) else it })
    }

    suspend fun create(name: String, description: String = ""): ReadingList {
        require(name.isNotBlank()) { "Reading list needs a name" }
        val list = ReadingList(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            description = description,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        write(all.first() + list)
        return list
    }

    /** Creates the "Want to read" pile when it does not exist yet, and returns it. */
    suspend fun ensureWantToRead(): ReadingList {
        all.first().firstOrNull { it.id == WANT_TO_READ_LIST_ID }?.let { return it }
        val pile = ReadingList(
            id = WANT_TO_READ_LIST_ID,
            name = "Want to read",
            description = "Series to try later.",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        write(all.first() + pile)
        return pile
    }

    suspend fun rename(id: String, name: String, description: String) {
        require(name.isNotBlank()) { "Reading list needs a name" }
        update(id) { it.copy(name = name.trim(), description = description) }
    }

    suspend fun delete(id: String) {
        write(all.first().filterNot { it.id == id })
    }

    suspend fun get(id: String): ReadingList? = all.first().firstOrNull { it.id == id }

    /** Adds a series at the end of the list, or moves it there when it is already on it. */
    suspend fun addSeries(id: String, seriesId: String, title: String, coverUrl: String? = null) {
        update(id) { list ->
            val entry = ReadingListEntry(seriesId, title, coverUrl, System.currentTimeMillis())
            list.copy(entries = list.entries.filterNot { it.seriesId == seriesId } + entry)
        }
    }

    suspend fun removeSeries(id: String, seriesId: String) {
        update(id) { list -> list.copy(entries = list.entries.filterNot { it.seriesId == seriesId }) }
    }

    /** Removes every series in [seriesIds] in a single write. */
    suspend fun removeEntries(id: String, seriesIds: Set<String>) {
        update(id) { list -> list.copy(entries = list.entries.filterNot { it.seriesId in seriesIds }) }
    }

    /**
     * Puts back entries removed earlier, in their old order. Entries added since the
     * removal stay on top, mirroring [mergeRestore].
     */
    suspend fun restoreEntries(id: String, snapshot: List<ReadingListEntry>) {
        update(id) { list ->
            val snapshotIds = snapshot.mapTo(HashSet()) { it.seriesId }
            val kept = list.entries.filter { it.seriesId !in snapshotIds }
            val restored = snapshot.map { entry -> list.entries.firstOrNull { it.seriesId == entry.seriesId } ?: entry }
            list.copy(entries = kept + restored)
        }
    }

    /** Moves a series to [toIndex], clamping into the list. */
    suspend fun moveSeries(id: String, seriesId: String, toIndex: Int) {
        update(id) { list ->
            val entries = list.entries.toMutableList()
            val from = entries.indexOfFirst { it.seriesId == seriesId }
            if (from < 0) return@update list
            val entry = entries.removeAt(from)
            entries.add(toIndex.coerceIn(0, entries.size), entry)
            list.copy(entries = entries)
        }
    }

    /** Sorts the entries in place: by title, or by when they were added (newest first). */
    suspend fun sort(id: String, byTitle: Boolean) {
        update(id) { list ->
            list.copy(
                entries = if (byTitle) list.entries.sortedBy { it.title.lowercase() }
                else list.entries.sortedByDescending { it.addedAt },
            )
        }
    }
}

/** A reading list as shareable plain text: one numbered title per line with the description on top. */
fun shareReadingListAsText(list: ReadingList): String = buildString {
    appendLine(list.name)
    if (list.description.isNotBlank()) appendLine(list.description)
    appendLine()
    list.entries.forEachIndexed { index, entry ->
        appendLine("${index + 1}. ${entry.title}")
    }
}.trimEnd()

/** A reading list as JSON, for backup or for importing on another device. */
fun exportReadingListJson(list: ReadingList): String =
    StoredJson.encodeToString(ReadingList.serializer(), list)

/** Reads back [exportReadingListJson] output. Null when the JSON is not a reading list. */
fun importReadingListJson(json: String): ReadingList? =
    decodeStored(ReadingList.serializer(), json)?.copy(id = UUID.randomUUID().toString())
