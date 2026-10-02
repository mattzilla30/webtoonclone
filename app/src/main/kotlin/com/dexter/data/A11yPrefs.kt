package com.dexter.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.a11yDataStore by preferencesDataStore(name = "a11y")

/** Which typeface the app's UI text renders in. */
enum class AppFont { System, OpenDyslexicStyle, CustomFile }

/** A colour-blind-safe UI theme, or None for the regular accent-coloured theme. */
enum class CvdTheme { None, DeuteranopiaSafe, ProtanopiaSafe, HighContrast }

/** Every accessibility preference, kept in its own store so this batch merges cleanly. */
data class A11yState(
    /** Read chapters aloud in the background. */
    val ttsEnabled: Boolean = false,
    /** Narration speed multiplier, 0.5 to 2.0. */
    val ttsSpeed: Float = 1f,
    /** Turn the page automatically as narration reaches it. */
    val ttsAutoAdvance: Boolean = true,
    /** Listen for "next page", "scroll down", "go back" in the reader. */
    val voiceControl: Boolean = false,
    val font: AppFont = AppFont.System,
    /** A user-picked .ttf or .otf, as a content URI string. Only used when [font] is [AppFont.CustomFile]. */
    val customFontUri: String = "",
    val cvdTheme: CvdTheme = CvdTheme.None,
    /** Split oversized vertical pages into screen-sized segments. */
    val tallPageSplit: Boolean = false,
    /** Split any page taller than this many screen heights. */
    val tallSplitScreens: Float = 3f,
)

/**
 * Accessibility preferences. Mirrors the [UpdateCheckStore] pattern: a preferences DataStore behind a
 * small class, with one [state] flow and a suspend setter per value.
 */
class A11yPrefs(private val context: Context) {
    private val keys = object {
        val ttsEnabled = booleanPreferencesKey("tts_enabled")
        val ttsSpeed = floatPreferencesKey("tts_speed")
        val ttsAutoAdvance = booleanPreferencesKey("tts_auto_advance")
        val voiceControl = booleanPreferencesKey("voice_control")
        val font = stringPreferencesKey("font")
        val customFontUri = stringPreferencesKey("custom_font_uri")
        val cvdTheme = stringPreferencesKey("cvd_theme")
        val tallPageSplit = booleanPreferencesKey("tall_page_split")
        val tallSplitScreens = floatPreferencesKey("tall_split_screens")
    }

    /** The whole a11y state as it changes. */
    val state: Flow<A11yState> = context.a11yDataStore.data.map { prefs ->
        A11yState(
            ttsEnabled = prefs[keys.ttsEnabled] ?: false,
            ttsSpeed = prefs[keys.ttsSpeed] ?: 1f,
            ttsAutoAdvance = prefs[keys.ttsAutoAdvance] ?: true,
            voiceControl = prefs[keys.voiceControl] ?: false,
            font = prefs[keys.font]?.let { runCatching { AppFont.valueOf(it) }.getOrNull() } ?: AppFont.System,
            customFontUri = prefs[keys.customFontUri] ?: "",
            cvdTheme = prefs[keys.cvdTheme]?.let { runCatching { CvdTheme.valueOf(it) }.getOrNull() } ?: CvdTheme.None,
            tallPageSplit = prefs[keys.tallPageSplit] ?: false,
            tallSplitScreens = prefs[keys.tallSplitScreens] ?: 3f,
        )
    }

    /** The current value without collecting, for services and receivers. */
    suspend fun current(): A11yState = state.first()

    suspend fun setTtsEnabled(on: Boolean) = update { it[keys.ttsEnabled] = on }
    suspend fun setTtsSpeed(speed: Float) = update { it[keys.ttsSpeed] = speed.coerceIn(0.5f, 2f) }
    suspend fun setTtsAutoAdvance(on: Boolean) = update { it[keys.ttsAutoAdvance] = on }
    suspend fun setVoiceControl(on: Boolean) = update { it[keys.voiceControl] = on }
    suspend fun setFont(font: AppFont) = update { it[keys.font] = font.name }
    suspend fun setCustomFontUri(uri: String) = update { it[keys.customFontUri] = uri }
    suspend fun setCvdTheme(theme: CvdTheme) = update { it[keys.cvdTheme] = theme.name }
    suspend fun setTallPageSplit(on: Boolean) = update { it[keys.tallPageSplit] = on }
    suspend fun setTallSplitScreens(screens: Float) = update { it[keys.tallSplitScreens] = screens.coerceIn(1.5f, 8f) }

    private suspend fun update(block: (MutablePreferences) -> Unit) {
        context.a11yDataStore.edit(block)
    }
}
