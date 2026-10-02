package com.dexter.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import com.dexter.data.ComicInfo

/**
 * Edits the ComicInfo.xml inside an exported CBZ. The caller loads the current metadata with
 * [com.dexter.data.readComicInfo] and writes the result with [com.dexter.data.writeComicInfo];
 * this dialog only edits the fields.
 */
@Composable
fun ComicInfoEditorDialog(
    fileName: String,
    info: ComicInfo,
    onSave: (ComicInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    var series by remember(info) { mutableStateOf(info.series) }
    var number by remember(info) { mutableStateOf(info.number) }
    var title by remember(info) { mutableStateOf(info.title) }
    var volume by remember(info) { mutableStateOf(info.volume.orEmpty()) }
    var translator by remember(info) { mutableStateOf(info.translator.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit metadata") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MetadataField("Series", series) { series = it }
                MetadataField("Number", number) { number = it }
                MetadataField("Title", title) { title = it }
                MetadataField("Volume", volume) { volume = it }
                MetadataField("Translator", translator) { translator = it }
                Text(
                    "Page count and web link are managed by Dexter and stay untouched.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(info.copy(series = series, number = number, title = title, volume = volume.ifBlank { null }, translator = translator.ifBlank { null }))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** One labeled text field in the metadata editor. */
@Composable
private fun MetadataField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
