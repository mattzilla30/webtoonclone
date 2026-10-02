package com.dexter.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.updateCheckDataStore by preferencesDataStore(name = "update_checks")

/** When the library check last ran for each subscribed series, so [Settings.seriesUpdateIntervals] can hold. */
class UpdateCheckStore(private val context: Context) {
    suspend fun markChecked(seriesId: String, at: Long = System.currentTimeMillis()) {
        context.updateCheckDataStore.edit { it[longPreferencesKey(seriesId)] = at }
    }

    suspend fun lastChecked(seriesId: String): Long? =
        context.updateCheckDataStore.data.first()[longPreferencesKey(seriesId)]

    suspend fun all(): Map<String, Long> =
        context.updateCheckDataStore.data.first().asMap().entries
            .mapNotNull { (key, value) -> (value as? Long)?.let { key.name to it } }
            .toMap()
}

/**
 * Whether [seriesId] is due for a chapter check. The per-series interval wins when it is positive; a
 * missing or zero entry follows the global [Settings.checkIntervalMinutes]. A series never checked is
 * always due. An interval shorter than the worker's schedule only applies as often as the worker runs.
 */
fun seriesUpdateDue(
    seriesId: String,
    lastCheckedAt: Long?,
    settings: Settings,
    now: Long = System.currentTimeMillis(),
): Boolean {
    if (lastCheckedAt == null) return true
    val minutes = settings.seriesUpdateIntervals[seriesId]?.takeIf { it > 0 } ?: settings.checkIntervalMinutes
    return now - lastCheckedAt >= minutes.coerceAtLeast(1) * 60_000L
}
