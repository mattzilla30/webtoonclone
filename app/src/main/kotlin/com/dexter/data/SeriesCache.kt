package com.dexter.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.seriesCacheDataStore by preferencesDataStore(name = "series_cache")
private val ENTRIES = stringPreferencesKey("entries")

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

/** Puts [added] first and drops any older copy of the same series, keeping at most [max]. */
fun mergeCache(old: List<CachedSeries>, added: CachedSeries, max: Int = MAX_CACHED_SERIES): List<CachedSeries> =
    (listOf(added) + old.filter { it.detail.summary.id != added.detail.summary.id }).take(max)

/** The most recently opened series pages, kept on the device. */
class SeriesCacheStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(CachedSeries.serializer())

    suspend fun save(series: CachedSeries) {
        context.seriesCacheDataStore.edit { prefs ->
            val old = prefs[ENTRIES]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
            prefs[ENTRIES] = json.encodeToString(serializer, mergeCache(old, series))
        }
    }

    suspend fun load(seriesId: String, language: String = "en"): CachedSeries? {
        val raw = context.seriesCacheDataStore.data.first()[ENTRIES] ?: return null
        val all = runCatching { json.decodeFromString(serializer, raw) }.getOrNull() ?: return null
        return all.firstOrNull { it.detail.summary.id == seriesId && it.language == language }
    }
}
