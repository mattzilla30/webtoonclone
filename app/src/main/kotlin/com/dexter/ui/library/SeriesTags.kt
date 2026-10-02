package com.dexter.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Adds and removes your own tags on the selected series. Tags are freeform and yours alone, unlike
 * the MangaDex genre and theme tags. With several series selected, [existing] is the union of their
 * tags: adding puts the tag on every one, removing takes it off every one that has it.
 */
@Composable
internal fun TagEditorDialog(
    count: Int,
    existing: Set<String>,
    suggestions: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val add = {
        val clean = text.trim()
        if (clean.isNotEmpty()) {
            onAdd(clean)
            text = ""
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Tags" else "Tags on $count series") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("New tag") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = add, modifier = Modifier.padding(start = 8.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Add tag")
                    }
                }
                if (existing.isNotEmpty()) {
                    Text(
                        "On ${if (count == 1) "this series" else "the selection"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    )
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        existing.sorted().forEach { tag ->
                            InputChip(
                                selected = false,
                                onClick = {},
                                label = { Text(tag) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Remove tag $tag",
                                        modifier = Modifier.size(InputChipDefaults.IconSize).clickable { onRemove(tag) },
                                    )
                                },
                            )
                        }
                    }
                }
                // The X on an existing chip removes it; tapping a suggestion's chip adds it.
                if (suggestions.isNotEmpty()) {
                    Text(
                        "Suggestions",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    )
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.sorted().forEach { tag ->
                            FilterChip(selected = false, onClick = { onAdd(tag) }, label = { Text(tag) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** Chips for the tags you use, to narrow the list to series carrying every selected tag. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagFilterRow(allTags: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    if (allTags.isEmpty()) return
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        allTags.forEach { tag ->
            FilterChip(
                selected = tag in selected,
                onClick = { onToggle(tag) },
                label = { Text(tag) },
                modifier = Modifier.semantics { role = Role.Checkbox },
            )
        }
    }
}
