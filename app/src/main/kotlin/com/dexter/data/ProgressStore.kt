package com.dexter.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "progress")

private const val PREFIX = "series_"

class ProgressStore(private val context: Context) {
    private fun key(seriesId: String) = stringPreferencesKey(PREFIX + seriesId)

    fun observe(seriesId: String): Flow<ReadingProgress?> =
        context.dataStore.data.map { prefs ->
            prefs[key(seriesId)]?.let(::parseProgress)
            // Any series' write re-emits the whole store, so skip the ones that did not change this series.
        }.distinctUntilChanged()

    suspend fun save(seriesId: String, chapterId: String, page: Int, fraction: Float = 0f) {
        context.dataStore.edit { it[key(seriesId)] = formatProgress(chapterId, page, fraction) }
    }

    /** Every saved position, keyed by series id, in the form [formatProgress] writes. */
    suspend fun export(): Map<String, String> =
        context.dataStore.data.first().asMap().entries
            .filter { it.key.name.startsWith(PREFIX) && it.value is String }
            .associate { it.key.name.removePrefix(PREFIX) to it.value as String }

    /** Replaces every saved position with [positions]. */
    suspend fun replaceAll(positions: Map<String, String>) {
        context.dataStore.edit { prefs ->
            prefs.asMap().keys.filter { it.name.startsWith(PREFIX) }.forEach { prefs.remove(it) }
            positions.forEach { (id, value) -> prefs[key(id)] = value }
        }
    }
}
