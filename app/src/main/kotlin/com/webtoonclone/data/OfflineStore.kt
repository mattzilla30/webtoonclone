package com.webtoonclone.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.offlineDataStore by preferencesDataStore(name = "offline")
private val UPDATES = stringPreferencesKey("updates")
private val SEARCHES = stringPreferencesKey("searches")
private const val MAX_CACHED_SEARCHES = 10

/** The first page of the Updates tab, saved so the tab opens offline. */
@Serializable
data class CachedUpdates(val entries: List<UpdateEntry>, val savedAt: Long)

/** The first page of one search, tag, or browse list. */
@Serializable
data class CachedSearch(val key: String, val series: List<SeriesSummary>, val savedAt: Long)

/** What identifies a search: its words or tag, and its order. Case and spacing do not matter. */
fun searchKey(title: String?, tag: String?, order: Order): String =
    "${title.orEmpty().trim().lowercase()}|${tag.orEmpty().trim().lowercase()}|${order.name}"

/** Puts [added] first, replaces an older copy of the same search, and keeps at most [max]. */
fun mergeSearches(old: List<CachedSearch>, added: CachedSearch, max: Int = MAX_CACHED_SEARCHES): List<CachedSearch> =
    (listOf(added) + old.filter { it.key != added.key }).take(max)

/** First pages of Updates and searches, kept on the device for when the network fails. */
class OfflineStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val searchList = ListSerializer(CachedSearch.serializer())

    suspend fun saveUpdates(entries: List<UpdateEntry>) {
        context.offlineDataStore.edit {
            it[UPDATES] = json.encodeToString(CachedUpdates.serializer(), CachedUpdates(entries, System.currentTimeMillis()))
        }
    }

    suspend fun loadUpdates(): CachedUpdates? {
        val raw = context.offlineDataStore.data.first()[UPDATES] ?: return null
        return runCatching { json.decodeFromString(CachedUpdates.serializer(), raw) }.getOrNull()
    }

    suspend fun saveSearch(key: String, series: List<SeriesSummary>) {
        context.offlineDataStore.edit { prefs ->
            val old = prefs[SEARCHES]?.let { runCatching { json.decodeFromString(searchList, it) }.getOrNull() }.orEmpty()
            prefs[SEARCHES] = json.encodeToString(searchList, mergeSearches(old, CachedSearch(key, series, System.currentTimeMillis())))
        }
    }

    suspend fun loadSearch(key: String): CachedSearch? {
        val raw = context.offlineDataStore.data.first()[SEARCHES] ?: return null
        return runCatching { json.decodeFromString(searchList, raw) }.getOrNull()?.firstOrNull { it.key == key }
    }
}
