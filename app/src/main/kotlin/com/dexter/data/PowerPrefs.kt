package com.dexter.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.powerDataStore by preferencesDataStore(name = "power")

/** Every power-user preference, kept in its own store so this batch merges cleanly. */
data class PowerState(
    /** Turn pages with a Bluetooth clicker or gamepad in the reader. */
    val gamepadReader: Boolean = false,
    /** Let Tasker and friends send Dexter automation broadcasts. */
    val taskerEnabled: Boolean = false,
    /** Suggest which copy to keep when the same series comes from several sources. */
    val duplicateHints: Boolean = true,
)

/**
 * Power-user preferences. Same shape as [A11yPrefs]: one [state] flow and a suspend setter per value.
 */
class PowerPrefs(private val context: Context) {
    private val gamepadKey = booleanPreferencesKey("gamepad_reader")
    private val taskerKey = booleanPreferencesKey("tasker_enabled")
    private val duplicatesKey = booleanPreferencesKey("duplicate_hints")

    /** The whole power-user state as it changes. */
    val state: Flow<PowerState> = context.powerDataStore.data.map { prefs ->
        PowerState(
            gamepadReader = prefs[gamepadKey] ?: false,
            taskerEnabled = prefs[taskerKey] ?: false,
            duplicateHints = prefs[duplicatesKey] ?: true,
        )
    }

    /** The current value without collecting, for receivers and key handlers. */
    suspend fun current(): PowerState = state.first()

    suspend fun setGamepadReader(on: Boolean) {
        context.powerDataStore.edit { it[gamepadKey] = on }
    }

    suspend fun setTaskerEnabled(on: Boolean) {
        context.powerDataStore.edit { it[taskerKey] = on }
    }

    suspend fun setDuplicateHints(on: Boolean) {
        context.powerDataStore.edit { it[duplicatesKey] = on }
    }
}
