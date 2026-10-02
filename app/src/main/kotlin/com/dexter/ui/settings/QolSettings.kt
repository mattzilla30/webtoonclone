package com.dexter.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dexter.data.CloudAccount
import com.dexter.data.CloudEntry
import com.dexter.data.CloudFileProvider
import com.dexter.data.CloudProviderType
import com.dexter.data.LibraryStore
import com.dexter.data.LocalSeries
import com.dexter.data.NasShare
import com.dexter.data.NasShareStore
import com.dexter.data.QolPrefs
import com.dexter.data.ReadingStatus
import com.dexter.data.WebDavAccount
import com.dexter.data.WebDavProvider
import com.dexter.data.cachedArchive
import com.dexter.data.scanLocalRoot
import com.dexter.data.toSavedSeries
import com.dexter.ui.library.SurpriseFilter
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.compose.koinInject
import java.io.File
import java.util.UUID

/**
 * Quality-of-life settings: spoiler-safe blur, the hidden-gems feed toggle, and the surprise-me
 * default filter. Backed by [QolPrefs], not the shared [com.dexter.data.Settings]. Wired into the
 * Settings screen by calling [QolSettingsSection]; see the insertion snippet in the task report.
 */
@Composable
fun QolSettingsSection(qol: QolPrefs) {
    val scope = rememberCoroutineScope()
    val spoilerBlur by qol.spoilerBlur.collectAsState(initial = false)
    val hiddenGems by qol.hiddenGemsEnabled.collectAsState(initial = true)
    val surpriseFilter by qol.surpriseFilter.collectAsState(initial = null)

    SectionTitle("Quality of life")
    SwitchRow(
        "Spoiler-safe blur",
        "Blur covers until a series is started, and blur chapter art ahead of your current position.",
        spoilerBlur,
    ) { on -> scope.launch { qol.setSpoilerBlur(on) } }
    SwitchRow(
        "Hidden gems feed",
        "Show high-rated but little-followed series in Discover.",
        hiddenGems,
    ) { on -> scope.launch { qol.setHiddenGemsEnabled(on) } }
    ChoiceRow(
        "Surprise-me filter",
        SurpriseFilter.entries.map { it to it.label },
        SurpriseFilter.entries.firstOrNull { it.name == surpriseFilter } ?: SurpriseFilter.ANY,
    ) { choice -> scope.launch { qol.setSurpriseFilter(choice.name.takeIf { it != SurpriseFilter.ANY.name }) } }
}

/**
 * NAS share bookmarks: add a share, pick its folder through the system file picker, and remove
 * shares. Streaming itself goes through [com.dexter.data.NasTreeReader]; see its KDoc for the
 * SMB limitation.
 */
@Composable
fun NasSharesSection(store: NasShareStore) {
    val scope = rememberCoroutineScope()
    val shares by store.shares.collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }
    var pendingShare by remember { mutableStateOf<NasShare?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        val share = pendingShare
        pendingShare = null
        if (uri != null && share != null) {
            scope.launch { store.attachTreeUri(share.id, uri) }
        } else if (share != null) {
            scope.launch { store.remove(share.id) }
        }
    }

    SectionTitle("NAS shares")
    CardRow {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (shares.isEmpty()) {
                Text(
                    "No shares yet. Add your NAS folder to browse and stream CBZ files from it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            shares.forEach { share ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(share.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (share.treeUri.isNotBlank()) "Connected" else "Tap + then pick its folder",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { scope.launch { store.remove(share.id) } }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove ${share.name}")
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { adding = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add NAS share")
                }
            }
        }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        var host by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add NAS share") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(host, { host = it }, label = { Text("Host (optional)") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isBlank()) return@TextButton
                        val share = NasShare(id = UUID.randomUUID().toString(), name = name.trim(), host = host.trim())
                        pendingShare = share
                        adding = false
                        scope.launch {
                            store.add(share)
                            // The folder pick grants the persisted SAF permission the bookmark needs.
                            picker.launch(null)
                        }
                    },
                ) { Text("Pick folder") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
}

/**
 * Local comics import: point Dexter at a folder of CBZ/CBR files and add what it finds to the
 * library. The path is a plain file-system path (a file manager shows it); Android 11+ needs the
 * "all files" permission for folders outside the app's own storage.
 */
@Composable
fun LocalImportSection(qol: QolPrefs, libraryStore: LibraryStore) {
    val scope = rememberCoroutineScope()
    val folder by qol.localFolder.collectAsState(initial = null)
    var editing by remember { mutableStateOf(false) }
    var path by remember(folder) { mutableStateOf(folder.orEmpty()) }
    var scanned by remember { mutableStateOf<List<LocalSeries>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    SectionTitle("Local comics")
    if (!editing) {
        InfoRow(
            "Comics folder",
            folder ?: "Not set. Dexter scans it for CBZ and CBR files.",
            onClick = { editing = true },
        )
    } else {
        OutlinedTextField(
            value = path,
            onValueChange = { path = it },
            label = { Text("Folder path") },
            placeholder = { Text("/storage/emulated/0/Comics") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    scope.launch { qol.setLocalFolder(path) }
                    editing = false
                    scanned = null
                },
            ) { Text("Save") }
            TextButton(onClick = { editing = false; path = folder.orEmpty() }) { Text("Cancel") }
        }
    }
    if (folder != null) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    message = null
                    scanned = null
                    scope.launch {
                        val found = runCatching { scanLocalRoot(File(folder)) }
                        scanned = found.getOrDefault(emptyList())
                        message = found.fold(
                            { "${scanned.orEmpty().size} series found" },
                            { "Could not read that folder: ${it.message}" },
                        )
                    }
                },
            ) { Text("Scan") }
            if (!scanned.isNullOrEmpty()) {
                TextButton(
                    onClick = {
                        scope.launch {
                            scanned.orEmpty().forEach { series ->
                                libraryStore.setStatus(series.toSavedSeries(), ReadingStatus.Reading)
                            }
                            message = "Added ${scanned.orEmpty().size} series to the library"
                            scanned = null
                        }
                    },
                ) { Text("Add ${scanned.orEmpty().size} to library") }
            }
        }
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp)) }
}

/**
 * WebDAV cloud accounts: add a server, connect with a per-session password, and browse its comic
 * archives. Google Drive and Dropbox need OAuth and are not connected here.
 */
@Composable
fun WebDavSection(qol: QolPrefs) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val client: OkHttpClient = koinInject()
    val accounts by qol.webdavAccounts.collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }
    var browsing by remember { mutableStateOf<WebDavAccount?>(null) }

    SectionTitle("Cloud (WebDAV)")
    if (accounts.isEmpty()) {
        Text(
            "No cloud accounts. Add your WebDAV server to browse its CBZ files.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
    accounts.forEach { account ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(account.name, style = MaterialTheme.typography.bodyLarge)
                Text(account.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { browsing = account }) { Text("Browse") }
            IconButton(onClick = { scope.launch { qol.removeWebdavAccount(account.name) } }) {
                Icon(Icons.Default.Delete, contentDescription = "Remove ${account.name}")
            }
        }
    }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        TextButton(onClick = { adding = true }) { Text("Add WebDAV server") }
    }
    if (adding) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var username by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add WebDAV server") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(url, { url = it }, label = { Text("Base URL") }, placeholder = { Text("https://nas.local/remote.php/dav/files/user/Manga") }, singleLine = true)
                    OutlinedTextField(username, { username = it }, label = { Text("Username (optional)") }, singleLine = true)
                    Text(
                        "The password is asked each time you connect and is never stored.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && url.isNotBlank(),
                    onClick = {
                        scope.launch { qol.addWebdavAccount(WebDavAccount(name.trim(), url.trim(), username.trim())) }
                        adding = false
                    },
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
    browsing?.let { account ->
        WebDavBrowser(
            account = account,
            client = client,
            cacheDir = context.cacheDir,
            onDismiss = { browsing = null },
        )
    }
}

/** Browses one WebDAV account: password per session, folders navigate, CBZ files cache locally. */
@Composable
private fun WebDavBrowser(
    account: WebDavAccount,
    client: OkHttpClient,
    cacheDir: java.io.File,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf<CloudFileProvider?>(null) }
    var path by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<CloudEntry>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun connect(pw: String) {
        scope.launch {
            message = null
            entries = null
            val provider = WebDavProvider(
                CloudAccount(account.name, CloudProviderType.WEBDAV, account.baseUrl, account.username, pw),
                client,
            )
            val listed = runCatching { provider.list("") }
            if (listed.isSuccess) {
                connected = provider
                entries = listed.getOrDefault(emptyList())
            } else {
                message = "Could not connect: ${listed.exceptionOrNull()?.message}"
            }
        }
    }

    fun browse(dir: String) {
        val provider = connected ?: return
        scope.launch {
            message = null
            entries = null
            path = dir
            val listed = runCatching { provider.list(dir) }
            entries = listed.getOrDefault(emptyList())
            if (listed.isFailure) message = "Could not list: ${listed.exceptionOrNull()?.message}"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(account.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (connected == null) {
                    OutlinedTextField(
                        password, { password = it }, label = { Text("Password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    TextButton(onClick = { connect(password) }, enabled = password.isNotEmpty()) { Text("Connect") }
                } else {
                    if (path.isNotEmpty()) {
                        TextButton(onClick = { browse(path.substringBeforeLast("/", "")) }) { Text("← Up") }
                    }
                    when (val list = entries) {
                        null -> Text("Loading…", style = MaterialTheme.typography.bodySmall)
                        else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (list.isEmpty()) Text("Empty folder.", style = MaterialTheme.typography.bodySmall)
                            list.forEach { entry ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = {
                                            if (entry.isDirectory) browse(entry.path)
                                            else scope.launch {
                                                message = "Caching ${entry.name}…"
                                                val file = runCatching { connected?.cachedArchive(entry, cacheDir) }.getOrNull()
                                                message = if (file != null) "Cached to ${file.name}" else "Could not cache ${entry.name}"
                                            }
                                        },
                                    ) { Text(if (entry.isDirectory) "📁 ${entry.name}" else entry.name) }
                                }
                            }
                        }
                    }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
