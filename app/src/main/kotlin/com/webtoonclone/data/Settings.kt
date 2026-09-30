package com.webtoonclone.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Every rating MangaDex uses, mildest first. */
val ContentRatings = listOf("safe", "suggestive", "erotica", "pornographic")

/** The ratings to request for [chosen], in MangaDex order. An empty choice falls back to safe so lists never go blank. */
fun ratingsFor(chosen: Set<String>): List<String> = ContentRatings.filter { it in chosen }.ifEmpty { listOf("safe") }

@Serializable
enum class ThemeMode { Dark, Light, System, Black }

@Serializable
enum class ReaderBackground { Dark, Black, White }

/** How the reader turns pages. Auto picks from the series' tags and original language. */
@Serializable
enum class ReadingMode { Auto, Vertical, PagedLtr, PagedRtl }

/** Everything the Settings screen and the reader options change. Defaults match the app before settings existed. */
@Serializable
data class Settings(
    val theme: ThemeMode = ThemeMode.Dark,
    /** Material You surfaces. Accents stay green. */
    val dynamicColor: Boolean = false,
    /** Load the smaller MangaDex image set to use less data. */
    val dataSaver: Boolean = false,
    /** Show the romanized original title instead of the English one. */
    val originalTitles: Boolean = false,
    /** Tell MangaDex whether each page image loaded, which it asks clients to do. */
    val reportImageLoads: Boolean = true,
    val quietHours: Boolean = false,
    val quietStartHour: Int = 22,
    val quietEndHour: Int = 7,
    val readerBackground: ReaderBackground = ReaderBackground.Dark,
    /** Reader dimming from 0 to 70 percent. */
    val readerDim: Int = 0,
    /** 0 is off, 1 to 5 scroll faster. */
    val autoScrollLevel: Int = 0,
    /** Volume keys scroll the reader by a page. */
    val volumeKeys: Boolean = false,
    /** Content ratings to show, using MangaDex names. All four by default. */
    val contentRatings: Set<String> = ContentRatings.toSet(),
    /** MangaDex language code for chapters, titles, and descriptions. */
    val language: String = "en",
    /** The scanlation group to prefer for each series, by series id. */
    val preferredGroups: Map<String, String> = emptyMap(),
    /** A reading mode chosen for one series. A series with no entry uses Auto. */
    val seriesReadingModes: Map<String, ReadingMode> = emptyMap(),
)

private val Context.settingsDataStore by preferencesDataStore(name = "settings")
private val SETTINGS = stringPreferencesKey("settings")

class SettingsStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<Settings> = context.settingsDataStore.data.map { prefs -> decode(prefs[SETTINGS]) }

    suspend fun current(): Settings = settings.first()

    suspend fun update(change: (Settings) -> Settings) {
        context.settingsDataStore.edit { prefs ->
            prefs[SETTINGS] = json.encodeToString(Settings.serializer(), change(decode(prefs[SETTINGS])))
        }
    }

    private fun decode(raw: String?): Settings =
        raw?.let { runCatching { json.decodeFromString<Settings>(it) }.getOrNull() } ?: Settings()
}
