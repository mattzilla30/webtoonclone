package com.dexter.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dexter.R
import com.dexter.data.ContentTags
import com.dexter.data.DemographicOptions
import com.dexter.data.Formats
import com.dexter.data.Genres
import com.dexter.data.OriginalLanguageOptions
import com.dexter.data.SearchFilters
import com.dexter.data.StatusOptions
import com.dexter.data.SuggestiveTags
import com.dexter.data.Themes
import com.dexter.data.languageName
import com.dexter.ui.ChoiceChip
import com.dexter.ui.theme.Green

private val Red = Color(0xFFE5484D)

/** Full-screen advanced search. Tags cycle through include, exclude, and off. Apply runs the search. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FiltersDialog(initial: SearchFilters, onApply: (SearchFilters) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(stringResource(R.string.filters), fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                TextButton(onClick = { draft = SearchFilters() }) { Text(stringResource(R.string.reset), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                TextButton(onClick = {
                    onApply(draft)
                    onDismiss()
                }) { Text(stringResource(R.string.apply), color = Green, fontWeight = FontWeight.Bold) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Heading("Status")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusOptions.forEach { value ->
                        ChoiceChip(value.replaceFirstChar { it.uppercase() }, value in draft.status) {
                            draft = draft.copy(status = draft.toggle(draft.status, value))
                        }
                    }
                }
                Heading("Demographic")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DemographicOptions.forEach { value ->
                        ChoiceChip(value.replaceFirstChar { it.uppercase() }, value in draft.demographics) {
                            draft = draft.copy(demographics = draft.toggle(draft.demographics, value))
                        }
                    }
                }
                Heading("Original language")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OriginalLanguageOptions.forEach { value ->
                        ChoiceChip(languageName(value), value in draft.originalLanguages) {
                            draft = draft.copy(originalLanguages = draft.toggle(draft.originalLanguages, value))
                        }
                    }
                }
                Heading("Year")
                OutlinedTextField(
                    value = draft.year?.toString().orEmpty(),
                    onValueChange = { text -> draft = draft.copy(year = text.filter(Char::isDigit).take(4).toIntOrNull()) },
                    placeholder = { Text(stringResource(R.string.any_year)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Heading("Tags")
                Text(stringResource(R.string.tap_once_to_include_twice_to_exclude_thr), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Match all", draft.matchAll) { draft = draft.copy(matchAll = true) }
                    ChoiceChip("Match any", !draft.matchAll) { draft = draft.copy(matchAll = false) }
                }
                TagGroup("Content", ContentTags, draft) { draft = draft.cycleTag(it) }
                TagGroup("Formats", Formats, draft) { draft = draft.cycleTag(it) }
                TagGroup("Genres", Genres.map { it.name }, draft) { draft = draft.cycleTag(it) }
                TagGroup("Suggestive", SuggestiveTags, draft) { draft = draft.cycleTag(it) }
                TagGroup("Themes", Themes, draft) { draft = draft.cycleTag(it) }
                Column(Modifier.padding(bottom = 24.dp)) {}
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagGroup(title: String, tags: List<String>, draft: SearchFilters, onCycle: (String) -> Unit) {
    Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.sortedBy { it.lowercase() }.forEach { tag ->
            val (label, background) = when (tag) {
                in draft.included -> "+ $tag" to Green
                in draft.excluded -> "- $tag" to Red
                else -> tag to MaterialTheme.colorScheme.surfaceVariant
            }
            Text(
                label,
                fontSize = 12.sp,
                color = if (tag in draft.included || tag in draft.excluded) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(background).clickable { onCycle(tag) }.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
