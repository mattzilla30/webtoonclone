package com.dexter.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.dexter.data.db.AppDatabase
import com.dexter.data.db.SearchEntity
import com.dexter.data.db.toEntity
import com.dexter.data.db.toSaved
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

private val Context.libraryDataStore by preferencesDataStore(name = "library")
private val LIBRARY = stringPreferencesKey("library")
private val HOME_CACHE = stringPreferencesKey("home_cache")
private val HOME_CACHE_AT = longPreferencesKey("home_cache_at")
private const val MAX_RECENT = 50
private const val MAX_SEARCHES = 10

/**
 * Whether the lists in the old single-file store should be copied into the database: only once, only
 * when the database is still empty, and only when there is something to copy.
 */
fun shouldMigrate(legacy: LibraryData, alreadyMigrated: Boolean, databaseIsEmpty: Boolean): Boolean =
    !alreadyMigrated && databaseIsEmpty &&
        (legacy.recent.isNotEmpty() || legacy.subscribed.isNotEmpty() || legacy.lists.isNotEmpty() || legacy.searches.isNotEmpty())

/**
 * Recent reads, subscriptions, reading lists, and search history, kept on the device. The lists live
 * in a Room database. Small settings and the saved home screen stay in a preferences file, as before.
 */
class LibraryStore(private val context: Context, private val db: AppDatabase) {
    private val json = Json { ignoreUnknownKeys = true }
    private val dao get() = db.library()

    // The home screen cache lives in the same file, so skip decoding when only that changed.
    private val scalars: Flow<LibraryData> = context.libraryDataStore.data
        .map { prefs -> prefs[LIBRARY] }
        .distinctUntilChanged()
        .map { raw -> decode(raw) }

    private val fresh: Flow<LibraryData> = flow {
        ensureMigrated()
        emitAll(
            combine(
                dao.observe(LibraryList.Recent.key),
                dao.observe(LibraryList.Subscribed.key),
                dao.observe(LibraryList.Lists.key),
                dao.observeSearches(),
                scalars,
            ) { recent, subscribed, lists, searches, scalar ->
                scalar.copy(
                    recent = recent.map { it.toSaved() },
                    subscribed = subscribed.map { it.toSaved() },
                    lists = lists.map { it.toSaved() },
                    searches = searches.map { it.term },
                )
            },
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * The library as it changes. Every screen that watches it shares one set of database queries, which
     * stop five seconds after the last screen leaves. Use [current] for a read that must see the latest writes.
     */
    val data: Flow<LibraryData> = fresh.shareIn(scope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), replay = 1)

    /** One fresh read of the library, straight from the database. */
    suspend fun current(): LibraryData = fresh.first()

    private val migration = Mutex()
    private var migrated = false

    /**
     * Copies the lists from the old single-file store into the database the first time. If the copy
     * fails the flag stays unset and the next launch tries again, and the old data is left in place.
     */
    private suspend fun ensureMigrated() {
        if (migrated) return
        migration.withLock {
            if (migrated) return
            val legacy = readScalars()
            if (!legacy.roomMigrated) {
                val done = runCatching {
                    val empty = dao.countSaved() == 0 && dao.countSearches() == 0
                    if (shouldMigrate(legacy, alreadyMigrated = false, databaseIsEmpty = empty)) {
                        db.withTransaction {
                            writeList(LibraryList.Recent, legacy.recent)
                            writeList(LibraryList.Subscribed, legacy.subscribed)
                            writeList(LibraryList.Lists, legacy.lists)
                            writeSearches(legacy.searches)
                        }
                    }
                    updateScalars { it.copy(roomMigrated = true) }
                }.isSuccess
                if (!done) return
            }
            migrated = true
        }
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

    suspend fun recordRecent(series: SavedSeries) = modify(LibraryList.Recent) { recent ->
        (listOf(series.copy(at = System.currentTimeMillis())) + recent.filterNot { it.id == series.id }).take(MAX_RECENT)
    }

    suspend fun removeRecent(ids: Set<String>) = modify(LibraryList.Recent) { recent -> recent.filterNot { it.id in ids } }

    suspend fun toggleSubscribed(series: SavedSeries) = modify(LibraryList.Subscribed) { subscribed ->
        if (subscribed.any { it.id == series.id }) subscribed.filterNot { it.id == series.id } else listOf(series) + subscribed
    }

    /** Records the newest chapter the app has told you about for a subscribed series. */
    suspend fun markKnown(seriesId: String, chapterId: String, chapterNumber: String) = modify(LibraryList.Subscribed) { subscribed ->
        subscribed.map {
            if (it.id == seriesId) it.copy(knownChapterId = chapterId, knownChapterNumber = chapterNumber) else it
        }
    }

    suspend fun setSeriesNotify(seriesId: String, enabled: Boolean) = modify(LibraryList.Subscribed) { subscribed ->
        subscribed.map { if (it.id == seriesId) it.copy(notify = enabled) else it }
    }

    suspend fun removeSubscribed(ids: Set<String>) = modify(LibraryList.Subscribed) { subscribed -> subscribed.filterNot { it.id in ids } }

    /** Puts a series in a reading list with [status], or takes it out of the lists when [status] is null. */
    suspend fun setStatus(series: SavedSeries, status: ReadingStatus?) = modify(LibraryList.Lists) { lists ->
        val rest = lists.filterNot { it.id == series.id }
        if (status == null) rest else listOf(series.copy(status = status)) + rest
    }

    suspend fun removeLists(ids: Set<String>) = modify(LibraryList.Lists) { lists -> lists.filterNot { it.id in ids } }

    /** Undoes a removal by merging the earlier list back in. See [mergeRestore]. */
    suspend fun restore(list: LibraryList, snapshot: List<SavedSeries>) = modify(list) { current -> mergeRestore(current, snapshot) }

    /** Adds the series to the collection [name], or removes it when it is already there. Creates the collection if needed. */
    suspend fun toggleCollection(name: String, series: SavedSeries) = updateScalars { data ->
        val current = data.collections[name].orEmpty()
        val next = if (current.any { it.id == series.id }) current.filterNot { it.id == series.id } else listOf(series) + current
        data.copy(collections = data.collections + (name to next))
    }

    /** Follows the author, recording the series that exist now as seen. Unfollows when already followed. */
    suspend fun toggleAuthor(id: String, name: String, currentSeriesIds: List<String>) = updateScalars { data ->
        if (data.followedAuthors.any { it.id == id }) {
            data.copy(followedAuthors = data.followedAuthors.filterNot { it.id == id })
        } else {
            data.copy(followedAuthors = data.followedAuthors + FollowedAuthor(id, name, currentSeriesIds))
        }
    }

    suspend fun markAuthorSeen(id: String, seriesIds: List<String>) = updateScalars { data ->
        data.copy(followedAuthors = data.followedAuthors.map { if (it.id == id) it.copy(knownIds = (it.knownIds + seriesIds).distinct()) else it })
    }

    /** Keeps [search] under its name, replacing one with the same name. */
    suspend fun saveSearch(search: SavedSearch) = updateScalars { data ->
        data.copy(savedSearches = listOf(search) + data.savedSearches.filterNot { it.name == search.name })
    }

    suspend fun deleteSavedSearch(name: String) = updateScalars { data ->
        data.copy(savedSearches = data.savedSearches.filterNot { it.name == name })
    }

    suspend fun createCollection(name: String) = updateScalars { data ->
        if (name in data.collections) data else data.copy(collections = data.collections + (name to emptyList()))
    }

    suspend fun deleteCollection(name: String) = updateScalars { it.copy(collections = it.collections - name) }

    suspend fun removeFromCollection(name: String, ids: Set<String>) = updateScalars { data ->
        data.copy(collections = data.collections + (name to data.collections[name].orEmpty().filterNot { it.id in ids }))
    }

    /** Undoes a removal from a collection by merging the earlier list back in. */
    suspend fun restoreCollection(name: String, snapshot: List<SavedSeries>) = updateScalars { data ->
        data.copy(collections = data.collections + (name to mergeRestore(data.collections[name].orEmpty(), snapshot)))
    }

    suspend fun addSearch(query: String) {
        ensureMigrated()
        db.withTransaction {
            val current = dao.getSearches().map { it.term }
            writeSearches((listOf(query) + current.filterNot { it == query }).take(MAX_SEARCHES))
        }
    }

    suspend fun removeSearch(query: String) {
        ensureMigrated()
        db.withTransaction { writeSearches(dao.getSearches().map { it.term }.filterNot { it == query }) }
    }

    suspend fun clearSearches() {
        ensureMigrated()
        dao.clearSearches()
    }

    suspend fun setSearchOrder(order: String) = updateScalars { it.copy(searchOrder = order) }

    suspend fun setSortAlphabetical(alphabetical: Boolean) = updateScalars { it.copy(sortAlphabetical = alphabetical) }

    /** Sets both sort flags at once, so each of the three sort modes is one saved state. */
    suspend fun setSort(alphabetical: Boolean, unreadFirst: Boolean) = updateScalars { it.copy(sortAlphabetical = alphabetical, sortUnreadFirst = unreadFirst) }

    suspend fun setNotifications(enabled: Boolean) = updateScalars { it.copy(notificationsEnabled = enabled) }

    /** Replaces everything in the library with [data], for restoring a backup. */
    suspend fun replaceAll(data: LibraryData) {
        ensureMigrated()
        db.withTransaction {
            writeList(LibraryList.Recent, data.recent)
            writeList(LibraryList.Subscribed, data.subscribed)
            writeList(LibraryList.Lists, data.lists)
            writeSearches(data.searches)
        }
        updateScalars {
            data.copy(recent = emptyList(), subscribed = emptyList(), lists = emptyList(), searches = emptyList(), roomMigrated = true)
        }
    }

    private suspend fun modify(list: LibraryList, change: (List<SavedSeries>) -> List<SavedSeries>) {
        ensureMigrated()
        db.withTransaction {
            val before = dao.get(list.key).map { it.toSaved() }
            val after = change(before)
            // A change that leaves the list as it was writes nothing, so no screen redraws for it.
            if (after != before) writeList(list, after)
        }
    }

    private suspend fun writeList(list: LibraryList, items: List<SavedSeries>) {
        dao.clear(list.key)
        dao.insertAll(items.mapIndexed { index, series -> series.toEntity(list.key, index) })
    }

    private suspend fun writeSearches(terms: List<String>) {
        dao.clearSearches()
        dao.insertSearches(terms.mapIndexed { index, term -> SearchEntity(term, index) })
    }

    private suspend fun readScalars(): LibraryData = decode(context.libraryDataStore.data.first()[LIBRARY])

    private suspend fun updateScalars(change: (LibraryData) -> LibraryData) {
        context.libraryDataStore.edit { prefs ->
            prefs[LIBRARY] = json.encodeToString(LibraryData.serializer(), change(decode(prefs[LIBRARY])))
        }
    }

    private fun decode(raw: String?): LibraryData = raw?.let { runCatching { json.decodeFromString<LibraryData>(it) }.getOrNull() } ?: LibraryData()
}
