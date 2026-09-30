package com.webtoonclone.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "progress")

class ProgressStore(private val context: Context) {

    private fun key(seriesId: String) = stringPreferencesKey("series_$seriesId")

    fun observe(seriesId: String): Flow<ReadingProgress?> =
        context.dataStore.data.map { prefs ->
            prefs[key(seriesId)]?.let { raw ->
                val (chapterId, page) = raw.split(":", limit = 2).let { it[0] to it.getOrNull(1) }
                ReadingProgress(chapterId, page?.toIntOrNull() ?: 0)
            }
        }

    suspend fun save(seriesId: String, chapterId: String, page: Int) {
        context.dataStore.edit { it[key(seriesId)] = "$chapterId:$page" }
    }
}
