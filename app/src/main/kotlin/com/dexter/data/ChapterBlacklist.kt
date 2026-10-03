package com.dexter.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.blacklistDataStore by preferencesDataStore(name = "chapter_blacklist")

/**
 * Chapters you never want to see again: they stay out of re-downloads, update checks, and chapter
 * listings. Keys are chapter ids grouped per series id, so a re-upload with a new id does not sneak
 * back in only if you blacklist it again.
 */
class ChapterBlacklist(private val context: Context) {
    private fun key(seriesId: String) = stringSetPreferencesKey("bl:$seriesId")

    /** The blacklisted chapter ids of one series, as they change. */
    fun blacklisted(seriesId: String): Flow<Set<String>> =
        context.blacklistDataStore.data.map { it[key(seriesId)].orEmpty() }

    /** Every series with at least one blacklisted chapter, and its chapter ids. */
    fun all(): Flow<Map<String, Set<String>>> = context.blacklistDataStore.data.map { prefs ->
        prefs.asMap().entries.mapNotNull { (key, value) ->
            val ids = (value as? Set<*>)?.mapNotNull { it as? String }?.toSet().orEmpty()
            if (key.name.startsWith("bl:") && ids.isNotEmpty()) key.name.removePrefix("bl:") to ids else null
        }.toMap()
    }

    /** True when [chapterId] is blacklisted for [seriesId]. */
    suspend fun isBlacklisted(seriesId: String, chapterId: String): Boolean =
        blacklisted(seriesId).first().contains(chapterId)

    /** Permanently exclude [chapterId] from downloads, update checks, and listings. */
    suspend fun add(seriesId: String, chapterId: String) {
        val k = key(seriesId)
        context.blacklistDataStore.edit { it[k] = (it[k].orEmpty() + chapterId) }
    }

    /** Undo [add]. */
    suspend fun remove(seriesId: String, chapterId: String) {
        val k = key(seriesId)
        context.blacklistDataStore.edit {
            val rest = it[k].orEmpty() - chapterId
            if (rest.isEmpty()) it.remove(k) else it[k] = rest
        }
    }

    /** The ids of one series that are not blacklisted. */
    suspend fun allowedIds(seriesId: String): Set<String> = blacklisted(seriesId).first()

    /** Replaces the whole blacklist with the backup's: series ids mapped to chapter ids. */
    suspend fun replaceAll(blacklist: Map<String, Set<String>>) {
        context.blacklistDataStore.edit { prefs ->
            prefs.asMap().keys.filter { it.name.startsWith("bl:") }.forEach { prefs.remove(it) }
            blacklist.forEach { (seriesId, chapterIds) ->
                if (chapterIds.isNotEmpty()) prefs[key(seriesId)] = chapterIds
            }
        }
    }
}

/** [chapters] without the blacklisted ones, keeping their order. */
fun List<Chapter>.withoutBlacklisted(blacklistedIds: Set<String>): List<Chapter> =
    filter { it.id !in blacklistedIds }
