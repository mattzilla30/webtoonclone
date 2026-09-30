package com.webtoonclone.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.libraryDataStore by preferencesDataStore(name = "library")
private val LIBRARY = stringPreferencesKey("library")
private val HOME_CACHE = stringPreferencesKey("home_cache")
private val HOME_CACHE_AT = longPreferencesKey("home_cache_at")
private const val MAX_RECENT = 50
private const val MAX_SEARCHES = 10

/** Recent reads, subscriptions, and search history, all kept on the device. */
class LibraryStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val data: Flow<LibraryData> = context.libraryDataStore.data.map { prefs ->
        prefs[LIBRARY]?.let { runCatching { json.decodeFromString<LibraryData>(it) }.getOrNull() }
            ?: LibraryData()
    }

    /** Keeps the last home content so the home screen can open offline. */
    suspend fun saveHome(content: HomeContent) {
        context.libraryDataStore.edit {
            it[HOME_CACHE] = json.encodeToString(HomeContent.serializer(), content)
            it[HOME_CACHE_AT] = System.currentTimeMillis()
        }
    }

    suspend fun loadHome(): CachedHome? {
        val prefs = context.libraryDataStore.data.first()
        val raw = prefs[HOME_CACHE] ?: return null
        val content = runCatching { json.decodeFromString<HomeContent>(raw) }.getOrNull() ?: return null
        return CachedHome(content, prefs[HOME_CACHE_AT] ?: 0L)
    }

    suspend fun recordRecent(series: SavedSeries) = update { lib ->
        val rest = lib.recent.filterNot { it.id == series.id }
        lib.copy(recent = (listOf(series.copy(at = System.currentTimeMillis())) + rest).take(MAX_RECENT))
    }

    suspend fun removeRecent(ids: Set<String>) = update { lib ->
        lib.copy(recent = lib.recent.filterNot { it.id in ids })
    }

    suspend fun toggleSubscribed(series: SavedSeries) = update { lib ->
        val exists = lib.subscribed.any { it.id == series.id }
        lib.copy(
            subscribed = if (exists) lib.subscribed.filterNot { it.id == series.id }
            else listOf(series) + lib.subscribed,
        )
    }

    /** Records the newest chapter the app has told you about for a subscribed series. */
    suspend fun markKnown(seriesId: String, chapterId: String, chapterNumber: String) = update { lib ->
        lib.copy(
            subscribed = lib.subscribed.map {
                if (it.id == seriesId) it.copy(knownChapterId = chapterId, knownChapterNumber = chapterNumber) else it
            },
        )
    }

    /** Undoes a removal by merging the earlier list back in. See [mergeRestore]. */
    suspend fun restore(subscribed: Boolean, snapshot: List<SavedSeries>) = update { lib ->
        if (subscribed) lib.copy(subscribed = mergeRestore(lib.subscribed, snapshot))
        else lib.copy(recent = mergeRestore(lib.recent, snapshot))
    }

    suspend fun removeSubscribed(ids: Set<String>) = update { lib ->
        lib.copy(subscribed = lib.subscribed.filterNot { it.id in ids })
    }

    suspend fun addSearch(query: String) = update { lib ->
        lib.copy(searches = (listOf(query) + lib.searches.filterNot { it == query }).take(MAX_SEARCHES))
    }

    suspend fun removeSearch(query: String) = update { lib ->
        lib.copy(searches = lib.searches.filterNot { it == query })
    }

    suspend fun dismissHint() = update { it.copy(hintDismissed = true) }

    suspend fun setSearchOrder(order: String) = update { it.copy(searchOrder = order) }

    suspend fun setSortAlphabetical(alphabetical: Boolean) = update { it.copy(sortAlphabetical = alphabetical) }

    suspend fun setNotifications(enabled: Boolean) = update { it.copy(notificationsEnabled = enabled) }

    suspend fun clearSearches() = update { it.copy(searches = emptyList()) }

    private suspend fun update(change: (LibraryData) -> LibraryData) {
        context.libraryDataStore.edit { prefs ->
            val current = prefs[LIBRARY]?.let { runCatching { json.decodeFromString<LibraryData>(it) }.getOrNull() }
                ?: LibraryData()
            prefs[LIBRARY] = json.encodeToString(LibraryData.serializer(), change(current))
        }
    }
}
