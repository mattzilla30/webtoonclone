package com.dexter.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.withTransaction
import com.dexter.data.db.AppDatabase
import com.dexter.data.db.SearchEntity
import com.dexter.data.db.toEntity
import com.dexter.data.db.toSaved
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
class LibraryStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val db by lazy { Room.databaseBuilder(context, AppDatabase::class.java, "library.db").build() }
    private val dao get() = db.library()

    private val scalars: Flow<LibraryData> = context.libraryDataStore.data.map { prefs -> decode(prefs[LIBRARY]) }

    val data: Flow<LibraryData> = flow {
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
        db.withTransaction { writeList(list, change(dao.get(list.key).map { it.toSaved() })) }
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
