package com.dexter.data.db

import androidx.room.AutoMigration
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
    /** Every saved series in every list, in list order. One query feeds all three lists. */
    @Query("SELECT * FROM saved_series ORDER BY listName, position")
    fun observeAll(): Flow<List<SavedSeriesEntity>>

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

/** A chapter saved on the device. The row is written last, so a row means every page file is there. */
@Entity(tableName = "downloads", indices = [Index("seriesId")])
data class DownloadEntity(
    @PrimaryKey val chapterId: String,
    val seriesId: String,
    val seriesTitle: String,
    val coverUrl: String?,
    val number: String,
    val title: String,
    val volume: String?,
    val groupName: String?,
    val publishedAt: String,
    val pageCount: Int,
    val bytes: Long,
    val savedAt: Long,
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY savedAt DESC")
    fun observe(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE seriesId = :seriesId")
    suspend fun forSeries(seriesId: String): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE chapterId = :chapterId")
    suspend fun get(chapterId: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: DownloadEntity)

    @Query("DELETE FROM downloads WHERE chapterId = :chapterId")
    suspend fun delete(chapterId: String)

    @Query("DELETE FROM downloads WHERE seriesId = :seriesId")
    suspend fun deleteSeries(seriesId: String)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()
}

/** One row per chapter you opened, with when you last opened it. Feeds the reading stats. */
@Entity(tableName = "read_events", indices = [Index("at")])
data class ReadEventEntity(
    @PrimaryKey val chapterId: String,
    val seriesId: String,
    val seriesTitle: String,
    val at: Long,
)

@Dao
interface StatsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: ReadEventEntity)

    @Query("SELECT * FROM read_events")
    fun observe(): Flow<List<ReadEventEntity>>
}

@Database(
    entities = [SavedSeriesEntity::class, SearchEntity::class, DownloadEntity::class, ReadEventEntity::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao

    abstract fun downloads(): DownloadDao

    abstract fun stats(): StatsDao
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
