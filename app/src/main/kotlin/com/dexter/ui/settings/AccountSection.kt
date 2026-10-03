package com.dexter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dexter.data.ANILIST_REDIRECT
import com.dexter.data.Accounts
import com.dexter.data.MAL_REDIRECT
import com.dexter.data.MangaDexLogin
import com.dexter.data.TrackerLogin

/** Sign in to MangaDex with your own API client, or, signed in, sync and choose what is sent. */
@Composable
internal fun MangaDexAccountSection(
    login: MangaDexLogin?,
    busy: Boolean,
    onSignIn: (clientId: String, clientSecret: String, username: String, password: String) -> Unit,
    onSync: () -> Unit,
    onSignOut: () -> Unit,
    onReadMarkers: (Boolean) -> Unit,
) {
    SettingsBlock("MangaDex account") {
        if (login != null) {
            InfoRow(
                title = "Signed in as ${login.username}",
                sortKey = "MangaDex account",
                subtitle = "Follows and subscriptions merge both ways. Nothing is removed on either side.",
                action = {
                    OutlinedButton(onClick = onSync, enabled = !busy) { Text("Sync") }
                },
            )
            SwitchRow("Send read markers", "Mark each chapter you open as read on MangaDex.", login.readMarkers, onReadMarkers)
            InfoRow(title = "Sign out", subtitle = "Forgets the sign-in on this phone.", sortKey = "MangaDex account", action = { OutlinedButton(onClick = onSignOut) { Text("Sign out") } })
            return@SettingsBlock
        }
        Searchable("MangaDex account", "sign in", "sync", "follows", "client", key = "MangaDex account") {
            var clientId by rememberSaveable { mutableStateOf("") }
            var clientSecret by rememberSaveable { mutableStateOf("") }
            var username by rememberSaveable { mutableStateOf("") }
            var password by rememberSaveable { mutableStateOf("") }
            Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Create a personal API client on mangadex.org under Settings, API Clients, then enter its id and secret with your account. " +
                        "The password signs you in once and is not kept.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CredentialField("Client id", clientId) { clientId = it }
                CredentialField("Client secret", clientSecret, secret = true) { clientSecret = it }
                CredentialField("Username", username) { username = it }
                CredentialField("Password", password, secret = true) { password = it }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    FilledTonalButton(
                        enabled = !busy && listOf(clientId, clientSecret, username, password).all { it.isNotBlank() },
                        onClick = {
                            onSignIn(clientId, clientSecret, username, password)
                            password = ""
                        },
                    ) { Text(if (busy) "Signing in..." else "Sign in") }
                }
            }
        }
    }
}

/** One single-line text field for a sign-in form. [secret] hides what you type. */
@Composable
internal fun CredentialField(label: String, value: String, secret: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** AniList and MyAnimeList: sign in with an app you registered there, or sign out. */
@Composable
internal fun TrackingSection(
    accounts: Accounts,
    onAniList: (clientId: String) -> Unit,
    onMal: (clientId: String) -> Unit,
    onSignOutAniList: () -> Unit,
    onSignOutMal: () -> Unit,
) {
    SettingsBlock("Tracking") {
        TrackerRow(
            name = "AniList",
            login = accounts.aniList,
            help = "Create a client at anilist.co under Settings, Developer, with the redirect URL $ANILIST_REDIRECT, then enter its id.",
            onSignIn = onAniList,
            onSignOut = onSignOutAniList,
        )
        TrackerRow(
            name = "MyAnimeList",
            login = accounts.myAnimeList,
            help = "Create an app at myanimelist.net under Account, API, with the type Android and the redirect URL $MAL_REDIRECT, then enter its client id.",
            onSignIn = onMal,
            onSignOut = onSignOutMal,
        )
    }
}

@Composable
private fun TrackerRow(name: String, login: TrackerLogin?, help: String, onSignIn: (String) -> Unit, onSignOut: () -> Unit) {
    if (login != null) {
        InfoRow(
            title = "$name: " + login.userName.ifBlank { "signed in" },
            subtitle = "Chapters you open move the series' progress forward there.",
            action = { OutlinedButton(onClick = onSignOut) { Text("Sign out") } },
        )
        return
    }
    Searchable(name, "tracking", "sign in", "progress", key = name) {
        var clientId by rememberSaveable(name) { mutableStateOf("") }
        Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CredentialField("Client id", clientId) { clientId = it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FilledTonalButton(enabled = clientId.isNotBlank(), onClick = { onSignIn(clientId) }) { Text("Sign in to $name") }
            }
        }
    }
}
