package com.dexter.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * A MangaDex sign-in through your own personal API client. The password is used once to sign in and is
 * never kept. The refresh token keeps you signed in until you sign out or MangaDex ends the session.
 */
@Serializable
data class MangaDexLogin(
    val clientId: String,
    val clientSecret: String,
    val username: String,
    val refreshToken: String,
    /** Send a read marker to MangaDex for each chapter you open. */
    val readMarkers: Boolean = true,
)

/** A tracker sign-in: AniList or MyAnimeList, with the app registration you made there. */
@Serializable
data class TrackerLogin(
    val clientId: String,
    val accessToken: String,
    val userName: String = "",
    /** When the access token stops working, in epoch milliseconds. 0 when unknown. */
    val expiresAt: Long = 0,
    /** MyAnimeList's tokens refresh. AniList's last a year and do not. */
    val refreshToken: String? = null,
    val clientSecret: String? = null,
)

/** Every account you signed in to. Kept in its own file, which device backups leave out. */
@Serializable
data class Accounts(
    val mangaDex: MangaDexLogin? = null,
    val aniList: TrackerLogin? = null,
    val myAnimeList: TrackerLogin? = null,
    /** Tracker media ids by MangaDex series id, so each series is looked up once. */
    val aniListIds: Map<String, Int> = emptyMap(),
    val malIds: Map<String, Int> = emptyMap(),
    /** A MyAnimeList sign-in under way in the browser: the app's client id and the PKCE verifier it sent. */
    val malPendingClientId: String? = null,
    val malVerifier: String? = null,
    /** The AniList client id of a sign-in under way in the browser. */
    val aniListPendingClientId: String? = null,
)

private val Context.accountsDataStore by preferencesDataStore(name = "accounts")
private val ACCOUNTS = stringPreferencesKey("accounts")

class AccountStore(private val context: Context) {
    val accounts: Flow<Accounts> = context.accountsDataStore.data.map { prefs -> decodeStored(Accounts.serializer(), prefs[ACCOUNTS]) ?: Accounts() }

    suspend fun current(): Accounts = accounts.first()

    suspend fun update(change: (Accounts) -> Accounts) {
        context.accountsDataStore.edit { prefs ->
            val now = decodeStored(Accounts.serializer(), prefs[ACCOUNTS]) ?: Accounts()
            prefs[ACCOUNTS] = StoredJson.encodeToString(Accounts.serializer(), change(now))
        }
    }
}
