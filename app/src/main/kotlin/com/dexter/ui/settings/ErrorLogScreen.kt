package com.dexter.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexter.data.ErrorLog
import com.dexter.data.errorLogText
import com.dexter.ui.AppTopBar
import java.text.DateFormat
import java.util.Date

/** The failures the app recorded, newest first. Tap one to see its stack trace. Share sends the whole log as text. */
@Composable
fun ErrorLogScreen(onBack: () -> Unit) {
    val entries by ErrorLog.entries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM) }
    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            "Error log",
            onBack,
            subtitle = "${entries.size} recorded",
            actions = {
                if (entries.isNotEmpty()) {
                    TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Dexter error log")
                            putExtra(Intent.EXTRA_TEXT, errorLogText(entries) { format.format(Date(it)) })
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    }) { Text("Share") }
                    TextButton(onClick = ErrorLog::clear) { Text("Clear") }
                }
            },
        )
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing has gone wrong yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp))
            }
            return
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(entries, key = { "${it.at}-${it.message.hashCode()}-${it.trace.hashCode()}" }) { entry ->
                var open by remember { mutableStateOf(false) }
                Surface(
                    onClick = { open = !open },
                    shape = MaterialTheme.shapes.medium,
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${format.format(Date(entry.at))} · ${entry.kind}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(entry.message, style = MaterialTheme.typography.bodyMedium, maxLines = if (open) Int.MAX_VALUE else 2)
                        if (open) {
                            SelectionContainer {
                                Text(
                                    entry.trace,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
