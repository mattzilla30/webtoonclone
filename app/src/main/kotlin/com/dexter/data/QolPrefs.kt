package com.dexter.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

private val Context.qolDataStore by preferencesDataStore(name = "qol")

/**
 * Preferences for the quality-of-life batch: spoiler blur and trope tag picks. Kept separate from [Settings] so this batch never touches the shared settings file.
 */
class QolPrefs(private val context: Context) {
    private val blurKey = booleanPreferencesKey("spoiler_blur")
    private val tropesKey = stringSetPreferencesKey("trope_picks")
    private val nasHostKey = stringPreferencesKey("nas_default_host")

    /** Blur covers, chapter art, and progress-adjacent UI ahead of the current position. */
    val spoilerBlur: Flow<Boolean> = context.qolDataStore.data.map { it[blurKey] == true }

    /** Trope tag ids the user picked for discovery filtering. */
    val tropePicks: Flow<Set<String>> = context.qolDataStore.data.map { it[tropesKey].orEmpty() }

    suspend fun setSpoilerBlur(on: Boolean) {
        context.qolDataStore.edit { it[blurKey] = on }
    }

    suspend fun setTropePicks(ids: Set<String>) {
        context.qolDataStore.edit { it[tropesKey] = ids }
    }

    suspend fun setNasDefaultHost(host: String) {
        context.qolDataStore.edit { it[nasHostKey] = host }
    }

    /** The NAS host the user opened last, prefilled when adding a share. */
    val nasDefaultHost: Flow<String?> = context.qolDataStore.data.map { it[nasHostKey] }

    private val localFolderKey = stringPreferencesKey("local_folder")

    /** The folder scanned for local CBZ/CBR comics, as a file path. */
    val localFolder: Flow<String?> = context.qolDataStore.data.map { it[localFolderKey] }

    suspend fun setLocalFolder(path: String?) {
        context.qolDataStore.edit { prefs ->
            if (path.isNullOrBlank()) prefs.remove(localFolderKey) else prefs[localFolderKey] = path
        }
    }

    private val webdavKey = stringPreferencesKey("webdav_accounts")

    /** WebDAV accounts, without passwords: the password is asked per session and never persisted. */
    val webdavAccounts: Flow<List<WebDavAccount>> = context.qolDataStore.data.map { prefs ->
        decodeStored(ListSerializer(WebDavAccount.serializer()), prefs[webdavKey]).orEmpty()
    }

    suspend fun addWebdavAccount(account: WebDavAccount) {
        val current = webdavAccounts.first().filterNot { it.name == account.name }
        context.qolDataStore.edit {
            it[webdavKey] = StoredJson.encodeToString(ListSerializer(WebDavAccount.serializer()), current + account)
        }
    }

    suspend fun removeWebdavAccount(name: String) {
        val current = webdavAccounts.first().filterNot { it.name == name }
        context.qolDataStore.edit {
            it[webdavKey] = StoredJson.encodeToString(ListSerializer(WebDavAccount.serializer()), current)
        }
    }

    private val oauthKey = stringPreferencesKey("oauth_accounts")

    /** OAuth cloud accounts (Drive/Dropbox): just a name and provider. Tokens live in CloudTokenStore. */
    val oauthAccounts: Flow<List<OAuthAccount>> = context.qolDataStore.data.map { prefs ->
        decodeStored(ListSerializer(OAuthAccount.serializer()), prefs[oauthKey]).orEmpty()
    }

    suspend fun addOauthAccount(account: OAuthAccount) {
        val current = oauthAccounts.first().filterNot { it.name == account.name }
        context.qolDataStore.edit {
            it[oauthKey] = StoredJson.encodeToString(ListSerializer(OAuthAccount.serializer()), current + account)
        }
    }

    suspend fun removeOauthAccount(name: String) {
        val current = oauthAccounts.first().filterNot { it.name == name }
        context.qolDataStore.edit {
            it[oauthKey] = StoredJson.encodeToString(ListSerializer(OAuthAccount.serializer()), current)
        }
    }
}

/** A WebDAV account without its password, for persistence. */
@Serializable
data class WebDavAccount(
    val name: String,
    val baseUrl: String,
    val username: String = "",
)

/** An OAuth cloud account: a name plus which provider it points at. */
@Serializable
data class OAuthAccount(
    val name: String,
    val provider: CloudProviderType,
)
