package com.dexter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dexter.data.ContentRatings
import com.dexter.data.Languages
import com.dexter.data.Settings
import com.dexter.data.ThemeMode

/** The first screen of a new install: language, content ratings, and theme. Everything here changes later in Settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FirstRunSetup(settings: Settings, onChange: ((Settings) -> Settings) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Welcome to Dexter", style = MaterialTheme.typography.headlineMediumEmphasized, modifier = Modifier.semantics { heading() })
        Text(
            "Pick a few things before you start. You can change each one later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        SetupHeading("Language")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Languages.forEach { language ->
                ChoiceChip(language.name, settings.language == language.code) { onChange { it.copy(language = language.code) } }
            }
        }

        SetupHeading("Content ratings")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ContentRatings.forEach { rating ->
                val on = rating in settings.contentRatings
                ChoiceChip(rating.replaceFirstChar { it.uppercase() }, on) {
                    onChange { current ->
                        val next = if (on) current.contentRatings - rating else current.contentRatings + rating
                        if (next.isEmpty()) current else current.copy(contentRatings = next)
                    }
                }
            }
        }
        Text(
            "Erotica and pornographic stay off unless you turn them on.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )

        SetupHeading("Theme")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ThemeMode.Dark to "Dark", ThemeMode.Black to "True black", ThemeMode.Light to "Light", ThemeMode.System to "System").forEach { (mode, label) ->
                ChoiceChip(label, settings.theme == mode) { onChange { it.copy(theme = mode) } }
            }
        }

        Button(
            onClick = { onChange { it.copy(setupDone = true) } },
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp).heightIn(min = ButtonDefaults.MediumContainerHeight),
        ) { Text("Start reading") }
    }
}

@Composable
private fun SetupHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp).semantics { heading() })
}
