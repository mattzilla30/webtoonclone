package com.dexter.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.dexter.data.ReadingStatus
import com.dexter.data.SavedSeries
import kotlinx.coroutines.flow.Flow

/**
 * One row per series per list. [listName] is "recent", "subscribed", or "lists", and [position] keeps
 * the list in the order the app shows it.
 */
@Entity(
    tableName = "saved_series",
    primaryKeys = ["listName", "seriesId"],
    indices = [Index(value = ["listName", "position"])],
)
data class SavedSeriesEntity(
    val listName: String,
    val seriesId: String,
    val position: Int,
    val title: String,
    val coverUrl: String?,
    val chapterId: String?,
    val chapterNumber: String?,
    val at: Long,
    val knownChapterId: String?,
    val knownChapterNumber: String?,
    val notify: Boolean,
    val status: String?,
)

@Entity(tableName = "search_history")
data class SearchEntity(
    @PrimaryKey val term: String,
    val position: Int,
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM saved_series WHERE listName = :list ORDER BY position")
    fun observe(list: String): Flow<List<SavedSeriesEntity>>

    @Query("SELECT * FROM saved_series WHERE listName = :list ORDER BY position")
    suspend fun get(list: String): List<SavedSeriesEntity>

    @Query("DELETE FROM saved_series WHERE listName = :list")
    suspend fun clear(list: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<SavedSeriesEntity>)

    @Query("SELECT COUNT(*) FROM saved_series")
    suspend fun countSaved(): Int

    @Query("SELECT * FROM search_history ORDER BY position")
    fun observeSearches(): Flow<List<SearchEntity>>

    @Query("SELECT * FROM search_history ORDER BY position")
    suspend fun getSearches(): List<SearchEntity>

    @Query("DELETE FROM search_history")
    suspend fun clearSearches()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearches(rows: List<SearchEntity>)

    @Query("SELECT COUNT(*) FROM search_history")
    suspend fun countSearches(): Int
}

@Database(entities = [SavedSeriesEntity::class, SearchEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
}

fun SavedSeries.toEntity(list: String, position: Int) = SavedSeriesEntity(
    listName = list,
    seriesId = id,
    position = position,
    title = title,
    coverUrl = coverUrl,
    chapterId = chapterId,
    chapterNumber = chapterNumber,
    at = at,
    knownChapterId = knownChapterId,
    knownChapterNumber = knownChapterNumber,
    notify = notify,
    status = status?.name,
)

fun SavedSeriesEntity.toSaved() = SavedSeries(
    id = seriesId,
    title = title,
    coverUrl = coverUrl,
    chapterId = chapterId,
    chapterNumber = chapterNumber,
    at = at,
    knownChapterId = knownChapterId,
    knownChapterNumber = knownChapterNumber,
    notify = notify,
    // An unknown status name, such as one from a newer version, reads as no status.
    status = status?.let { name -> ReadingStatus.entries.firstOrNull { it.name == name } },
)
