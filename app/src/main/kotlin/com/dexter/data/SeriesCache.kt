package com.dexter.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File

/** How many series pages are kept, and how many chapters of each. */
const val MAX_CACHED_SERIES = 10
const val MAX_CACHED_CHAPTERS = 200

/** A series page saved on the device so it can open without a network. */
@Serializable
data class CachedSeries(
    val detail: SeriesDetail,
    /** Newest first, as the series page lists them. */
    val chapters: List<Chapter>,
    val savedAt: Long,
    /** The language the chapters were listed in. A copy in another language is not shown. */
    val language: String = "en",
)

/** The file name for one series in one language. Series ids are UUIDs and language codes are letters and dashes. */
fun cacheFileName(seriesId: String, language: String): String = "${seriesId}_$language.json"

/** Of [files] as (name, last saved time), the ones past the newest [max], which the cache deletes. */
fun cacheFilesToDrop(files: List<Pair<String, Long>>, max: Int = MAX_CACHED_SERIES): List<String> =
    files.sortedByDescending { it.second }.drop(max).map { it.first }

/**
 * The most recently opened series pages, one small file each. Opening a chapter or a series reads only the
 * file for that series, where the old single store decoded every saved series to find one.
 */
@OptIn(ExperimentalSerializationApi::class)
class SeriesCacheStore(private val context: Context) {
    private val json = StoredJson
    private val dir = File(context.filesDir, "series_cache")

    @Volatile private var oldStoreRemoved = false

    suspend fun save(series: CachedSeries) = withContext(Dispatchers.IO) {
        removeOldStore()
        dir.mkdirs()
        val target = File(dir, cacheFileName(series.detail.summary.id, series.language))
        val temp = File(dir, target.name + ".part")
        temp.outputStream().buffered().use { json.encodeToStream(CachedSeries.serializer(), series, it) }
        if (!temp.renameTo(target)) temp.delete()
        val files = dir.listFiles { file -> file.name.endsWith(".json") }.orEmpty().map { it.name to it.lastModified() }
        cacheFilesToDrop(files).forEach { File(dir, it).delete() }
    }

    suspend fun load(seriesId: String, language: String = "en"): CachedSeries? = withContext(Dispatchers.IO) {
        val file = File(dir, cacheFileName(seriesId, language))
        if (!file.exists()) return@withContext null
        runCatching { file.inputStream().buffered().use { json.decodeFromStream(CachedSeries.serializer(), it) } }.getOrNull()
    }

    /** The cache used to live in one preferences file. It is only a cache, so the old copy is deleted, not moved. */
    private fun removeOldStore() {
        if (oldStoreRemoved) return
        File(context.filesDir, "datastore/series_cache.preferences_pb").delete()
        oldStoreRemoved = true
    }
}
