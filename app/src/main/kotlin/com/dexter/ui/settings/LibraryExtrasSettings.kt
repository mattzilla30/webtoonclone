package com.dexter.ui.settings

import androidx.compose.runtime.Composable
import com.dexter.data.Settings

/** Library extras: the on-device "Because you read" recommendations on Home. */
@Composable
fun LibraryExtrasSection(settings: Settings, update: ((Settings) -> Settings) -> Unit) {
    SectionTitle("Library extras")
    SwitchRow(
        "Recommendations",
        "Show \"Because you read\" picks on Home. Candidates are scored on this device from your library and reading history.",
        settings.recommendations,
    ) { on -> update { it.copy(recommendations = on) } }
}
