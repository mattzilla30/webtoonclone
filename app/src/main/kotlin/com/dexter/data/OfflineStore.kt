package com.dexter.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

private val Context.offlineDataStore by preferencesDataStore(name = "offline")
private val UPDATES = stringPreferencesKey("updates")
private val SEARCHES = stringPreferencesKey("searches")
private const val MAX_CACHED_SEARCHES = 10

/** The first page of the Updates tab, saved so the tab opens offline. */
@Serializable
data class CachedUpdates(val entries: List<UpdateEntry>, val savedAt: Long, val language: String = "en")

/** The first page of one search, tag, or browse list. */
@Serializable
data class CachedSearch(val key: String, val series: List<SeriesSummary>, val savedAt: Long)

/** What identifies a search: its words or tag, and its order. Case and spacing do not matter. */
fun searchKey(
    title: String?,
    tag: String?,
    order: Order,
    filters: SearchFilters = SearchFilters(),
    language: String = "en",
): String = listOf(
    title.orEmpty().trim().lowercase(),
    tag.orEmpty().trim().lowercase(),
    order.name,
    filters.included.sorted().joinToString(",").lowercase(),
    filters.excluded.sorted().joinToString(",").lowercase(),
    filters.status.sorted().joinToString(","),
    filters.demographics.sorted().joinToString(","),
    filters.originalLanguages.sorted().joinToString(","),
    filters.year?.toString().orEmpty(),
    if (filters.matchAll) "all" else "any",
    language,
).joinToString("|")

/** Puts [added] first, replaces an older copy of the same search, and keeps at most [max]. */
fun mergeSearches(old: List<CachedSearch>, added: CachedSearch, max: Int = MAX_CACHED_SEARCHES): List<CachedSearch> =
    (listOf(added) + old.filter { it.key != added.key }).take(max)

/** First pages of Updates and searches, kept on the device for when the network fails. */
class OfflineStore(private val context: Context) {
    private val json = StoredJson
    private val searchList = ListSerializer(CachedSearch.serializer())

    suspend fun saveUpdates(entries: List<UpdateEntry>, language: String = "en") {
        context.offlineDataStore.edit {
            it[UPDATES] = json.encodeToString(CachedUpdates.serializer(), CachedUpdates(entries, System.currentTimeMillis(), language))
        }
    }

    suspend fun loadUpdates(language: String = "en"): CachedUpdates? = withContext(Dispatchers.Default) {
        val raw = context.offlineDataStore.data.first()[UPDATES] ?: return@withContext null
        runCatching { json.decodeFromString(CachedUpdates.serializer(), raw) }.getOrNull()?.takeIf { it.language == language }
    }

    suspend fun saveSearch(key: String, series: List<SeriesSummary>) {
        context.offlineDataStore.edit { prefs ->
            val old = prefs[SEARCHES]?.let { runCatching { json.decodeFromString(searchList, it) }.getOrNull() }.orEmpty()
            prefs[SEARCHES] = json.encodeToString(searchList, mergeSearches(old, CachedSearch(key, series, System.currentTimeMillis())))
        }
    }

    suspend fun loadSearch(key: String): CachedSearch? = withContext(Dispatchers.Default) {
        val raw = context.offlineDataStore.data.first()[SEARCHES] ?: return@withContext null
        runCatching { json.decodeFromString(searchList, raw) }.getOrNull()?.firstOrNull { it.key == key }
    }
}
