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

private val Context.smartListDataStore by preferencesDataStore(name = "smart_lists")

private val smartListsKey = stringPreferencesKey("smart_lists")

/**
 * A saved advanced library query that behaves like an auto-updating list: "unread completed
 * seinen", "status:reading tag:horror", and so on. The query text uses the [parseLibraryQuery]
 * syntax; the list re-evaluates every time the library changes, so nothing is stored per series.
 *
 * This is the library-side counterpart to [SavedSearch], which lives on the search screen and
 * watches for new series. A SmartList watches the series you already have.
 */
@Serializable
data class SmartList(
    val name: String,
    val queryText: String,
    val createdAt: Long = 0,
)

/** Named smart lists, persisted as JSON. */
class SmartListStore(private val context: Context) {
    val all: Flow<List<SmartList>> = context.smartListDataStore.data.map { prefs ->
        decodeStored(ListSerializer(SmartList.serializer()), prefs[smartListsKey]).orEmpty()
    }

    private suspend fun write(next: List<SmartList>) {
        context.smartListDataStore.edit {
            it[smartListsKey] = StoredJson.encodeToString(ListSerializer(SmartList.serializer()), next)
        }
    }

    /** Saves a smart list, replacing one with the same name. Blank names and queries are rejected. */
    suspend fun save(name: String, queryText: String) {
        require(name.isNotBlank()) { "Smart list needs a name" }
        require(queryText.isNotBlank()) { "Smart list needs a query" }
        parseLibraryQuery(queryText)
        val current = all.first().filterNot { it.name.equals(name, ignoreCase = true) }
        write(current + SmartList(name.trim(), queryText, System.currentTimeMillis()))
    }

    suspend fun delete(name: String) {
        write(all.first().filterNot { it.name.equals(name, ignoreCase = true) })
    }

    suspend fun rename(from: String, to: String) {
        require(to.isNotBlank()) { "Smart list needs a name" }
        write(all.first().map { if (it.name.equals(from, ignoreCase = true)) it.copy(name = to.trim()) else it })
    }
}

/**
 * Evaluates a smart list against the library. [metas] carries per-series metadata for the query's
 * field prefixes; series without metadata match only the fields the library itself stores.
 */
fun evaluateSmartList(
    list: SmartList,
    series: List<SavedSeries>,
    metas: Map<String, LibrarySeriesMeta> = emptyMap(),
): List<SavedSeries> {
    val query = parseLibraryQuery(list.queryText)
    return series.filter { query.matches(it, metas[it.id]) }
}

/** Convenience overload for a raw query string. Null when the query does not parse. */
fun evaluateSmartListOrNull(
    queryText: String,
    series: List<SavedSeries>,
    metas: Map<String, LibrarySeriesMeta> = emptyMap(),
): List<SavedSeries>? = runCatching {
    series.filter { parseLibraryQuery(queryText).matches(it, metas[it.id]) }
}.getOrNull()
