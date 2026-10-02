package com.dexter.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Manual OAuth 2.0 (authorization-code flow with PKCE) for Google Drive and Dropbox. No AppAuth
 * dependency: the app opens the provider's consent page with a plain ACTION_VIEW intent and the
 * provider sends the user back to a dexter://oauth/... deep link handled by [CloudOAuth].
 *
 * The user supplies their own OAuth credentials: a Google Cloud Console OAuth client ID
 * ("Desktop app" type, which allows custom-scheme redirects) for Drive, and a Dropbox App Console
 * app key for Dropbox. The app cannot ship a shared client ID, so Settings must collect these.
 * Client IDs are public identifiers, not secrets, and live in app-private DataStore.
 *
 * Security note: [CloudAccount]'s KDoc says passwords live only in memory, but OAuth refresh
 * tokens are a deliberate, documented exception. They must persist to be useful (re-consenting on
 * every launch would be unusable), so they live in this app-private DataStore, the same trust
 * level as the rest of the app's private data. Access tokens stay in memory: [OAuthTokens] is
 * read from the store per request and never cached anywhere else.
 */
private val Context.oauthDataStore by preferencesDataStore(name = "oauth")

/** Tokens for one signed-in cloud account. [expiresAtMs] is a wall-clock estimate, not exact. */
data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMs: Long,
)

/**
 * Persists OAuth tokens and client IDs in app-private DataStore, keyed by provider plus account
 * name. Mirrors the *Prefs pattern (small class over a preferences DataStore, suspend setters).
 */
class CloudTokenStore(private val context: Context) {
    private val keys = object {
        fun access(provider: CloudProviderType, account: String) =
            stringPreferencesKey("access_${provider.name}_${keyOf(account)}")
        fun refresh(provider: CloudProviderType, account: String) =
            stringPreferencesKey("refresh_${provider.name}_${keyOf(account)}")
        fun expiry(provider: CloudProviderType, account: String) =
            longPreferencesKey("expiry_${provider.name}_${keyOf(account)}")
        fun clientId(provider: CloudProviderType) = stringPreferencesKey("client_id_${provider.name}")
    }

    /** Account names become DataStore-safe key parts. Collisions across names are acceptable here. */
    private fun keyOf(account: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(account.toByteArray(Charsets.UTF_8))

    /** The stored tokens for an account, or null when it never signed in (or signed out). */
    suspend fun tokens(provider: CloudProviderType, accountName: String): OAuthTokens? =
        context.oauthDataStore.data.map { prefs ->
            val access = prefs[keys.access(provider, accountName)] ?: return@map null
            val refresh = prefs[keys.refresh(provider, accountName)] ?: return@map null
            OAuthTokens(access, refresh, prefs[keys.expiry(provider, accountName)] ?: 0L)
        }.first()

    /** True when this account has stored tokens. */
    suspend fun isSignedIn(provider: CloudProviderType, accountName: String): Boolean =
        tokens(provider, accountName) != null

    suspend fun saveTokens(provider: CloudProviderType, accountName: String, tokens: OAuthTokens) {
        context.oauthDataStore.edit { prefs ->
            prefs[keys.access(provider, accountName)] = tokens.accessToken
            prefs[keys.refresh(provider, accountName)] = tokens.refreshToken
            prefs[keys.expiry(provider, accountName)] = tokens.expiresAtMs
        }
    }

    /** Forgets an account's tokens. The account itself (name, client ID) is kept in Settings. */
    suspend fun clearTokens(provider: CloudProviderType, accountName: String) {
        context.oauthDataStore.edit { prefs ->
            prefs.remove(keys.access(provider, accountName))
            prefs.remove(keys.refresh(provider, accountName))
            prefs.remove(keys.expiry(provider, accountName))
        }
    }

    /** The user-supplied OAuth client ID (Google) or app key (Dropbox) for a provider. */
    suspend fun clientId(provider: CloudProviderType): String? =
        context.oauthDataStore.data.map { it[keys.clientId(provider)]?.ifBlank { null } }.first()

    suspend fun setClientId(provider: CloudProviderType, clientId: String) {
        context.oauthDataStore.edit { it[keys.clientId(provider)] = clientId.trim() }
    }
}

/** A PKCE verifier: 64 random URL-safe characters, inside the 43-128 range the spec requires. */
fun newCodeVerifier(random: SecureRandom = SecureRandom()): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(48).also(random::nextBytes))

/** The PKCE challenge for a verifier: BASE64URL(SHA-256(verifier)), no padding (S256 method). */
fun codeChallenge(verifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}

/** Parses a token endpoint response: access_token, refresh_token (absent on some refreshes), expires_in. */
internal fun parseTokenResponse(json: String, keepRefreshToken: String? = null): OAuthTokens {
    val obj = Json.parseToJsonElement(json).jsonObject
    val access = obj["access_token"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        ?: throw IOException("Token response had no access_token")
    val refresh = obj["refresh_token"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        ?: keepRefreshToken
        ?: throw IOException("Token response had no refresh_token")
    val expiresIn = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
    // Five-minute safety margin so we refresh before the token actually dies.
    val expiresAt = System.currentTimeMillis() + (expiresIn - 300).coerceAtLeast(60) * 1000
    return OAuthTokens(access, refresh, expiresAt)
}

private suspend fun postTokenForm(client: OkHttpClient, url: String, form: FormBody): String =
    withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).post(form).build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Token request failed (${response.code}): ${body.take(200)}")
            body
        }
    }

/** Google Drive OAuth. Register a "Desktop app" OAuth client in Google Cloud Console and paste its client ID into Settings. */
object DriveOAuth {
    /** Where Google sends the user back; registered implicitly for Desktop-type clients. */
    const val REDIRECT_URI = "dexter://oauth/drive"
    private const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val TOKEN_URL = "https://oauth2.googleapis.com/token"

    /** The consent page URL. [clientId] is the user's own Google OAuth client ID. */
    fun authUrl(clientId: String, codeChallenge: String): String =
        "$AUTH_URL?client_id=${Uri.encode(clientId.trim())}" +
            "&redirect_uri=${Uri.encode(REDIRECT_URI)}" +
            "&response_type=code" +
            "&scope=${Uri.encode("https://www.googleapis.com/auth/drive.readonly")}" +
            "&code_challenge=$codeChallenge" +
            "&code_challenge_method=S256" +
            "&access_type=offline" +
            "&prompt=consent"

    /** Exchanges the authorization code for tokens. Throws [IOException] on failure. */
    suspend fun exchangeCode(
        client: OkHttpClient,
        clientId: String,
        code: String,
        codeVerifier: String,
    ): OAuthTokens = parseTokenResponse(
        postTokenForm(
            client, TOKEN_URL,
            FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("client_id", clientId.trim())
                .add("redirect_uri", REDIRECT_URI)
                .add("code_verifier", codeVerifier)
                .build(),
        ),
    )

    /** Refreshes an expired access token. Google omits refresh_token here, so the old one is kept. */
    suspend fun refresh(client: OkHttpClient, clientId: String, refreshToken: String): OAuthTokens =
        parseTokenResponse(
            postTokenForm(
                client, TOKEN_URL,
                FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refreshToken)
                    .add("client_id", clientId.trim())
                    .build(),
            ),
            keepRefreshToken = refreshToken,
        )
}

/** Dropbox OAuth. Create an app in the Dropbox App Console and paste its app key into Settings. */
object DropboxOAuth {
    /** Where Dropbox sends the user back; add it to the app's redirect URIs in the App Console. */
    const val REDIRECT_URI = "dexter://oauth/dropbox"
    private const val AUTH_URL = "https://www.dropbox.com/oauth2/authorize"
    private const val TOKEN_URL = "https://api.dropboxapi.com/oauth2/token"

    /** The consent page URL. [appKey] is the user's own Dropbox app key. PKCE means no client secret is sent. */
    fun authUrl(appKey: String, codeChallenge: String): String =
        "$AUTH_URL?client_id=${Uri.encode(appKey.trim())}" +
            "&redirect_uri=${Uri.encode(REDIRECT_URI)}" +
            "&response_type=code" +
            "&code_challenge=$codeChallenge" +
            "&code_challenge_method=S256" +
            "&token_access_type=offline"

    /** Exchanges the authorization code for tokens. Throws [IOException] on failure. */
    suspend fun exchangeCode(
        client: OkHttpClient,
        appKey: String,
        code: String,
        codeVerifier: String,
    ): OAuthTokens = parseTokenResponse(
        postTokenForm(
            client, TOKEN_URL,
            FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("client_id", appKey.trim())
                .add("redirect_uri", REDIRECT_URI)
                .add("code_verifier", codeVerifier)
                .build(),
        ),
    )

    /** Refreshes an expired access token. Dropbox omits refresh_token here, so the old one is kept. */
    suspend fun refresh(client: OkHttpClient, appKey: String, refreshToken: String): OAuthTokens =
        parseTokenResponse(
            postTokenForm(
                client, TOKEN_URL,
                FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refreshToken)
                    .add("client_id", appKey.trim())
                    .build(),
            ),
            keepRefreshToken = refreshToken,
        )
}

/**
 * Runs the OAuth sign-in flow both ways: [startDriveSignIn]/[startDropboxSignIn] build the consent
 * URL (the UI opens it with a plain ACTION_VIEW intent), and [handleRedirect] finishes the flow
 * when the browser sends the user back to dexter://oauth/... Mirrors Trackers.handleRedirect:
 * returns a user-facing message, or null when the URI is not an OAuth redirect.
 *
 * Pending verifiers live in memory only, keyed by provider, and are consumed by [handleRedirect].
 * If the process dies mid-flow the pending entry is gone and the user must start again; that is
 * safe because the verifier never leaves the device anyway.
 */
object CloudOAuth {
    private data class PendingFlow(val accountName: String, val verifier: String)

    private val pending = java.util.concurrent.ConcurrentHashMap<CloudProviderType, PendingFlow>()

    /**
     * Starts Drive sign-in for [accountName]: remembers the client ID and a fresh PKCE verifier,
     * and returns the consent URL for the UI to open in a browser. One pending flow per provider;
     * starting again replaces the old one.
     */
    suspend fun startDriveSignIn(
        store: CloudTokenStore,
        accountName: String,
        clientId: String,
    ): String {
        require(clientId.isNotBlank()) { "A Google OAuth client ID is required" }
        store.setClientId(CloudProviderType.GOOGLE_DRIVE, clientId)
        val verifier = newCodeVerifier()
        pending[CloudProviderType.GOOGLE_DRIVE] = PendingFlow(accountName.trim(), verifier)
        return DriveOAuth.authUrl(clientId, codeChallenge(verifier))
    }

    /** Starts Dropbox sign-in for [accountName]; same shape as [startDriveSignIn]. */
    suspend fun startDropboxSignIn(
        store: CloudTokenStore,
        accountName: String,
        appKey: String,
    ): String {
        require(appKey.isNotBlank()) { "A Dropbox app key is required" }
        store.setClientId(CloudProviderType.DROPBOX, appKey)
        val verifier = newCodeVerifier()
        pending[CloudProviderType.DROPBOX] = PendingFlow(accountName.trim(), verifier)
        return DropboxOAuth.authUrl(appKey, codeChallenge(verifier))
    }

    /**
     * Finishes a sign-in when the browser returns to dexter://oauth/drive or
     * dexter://oauth/dropbox with ?code=.... Returns a message for a toast, or null when [uri]
     * is not an OAuth redirect.
     */
    suspend fun handleRedirect(
        uri: Uri,
        client: OkHttpClient,
        store: CloudTokenStore,
    ): String? = withContext(Dispatchers.IO) {
        if (uri.scheme != "dexter" || uri.host != "oauth") return@withContext null
        val provider = when (uri.path) {
            "/drive" -> CloudProviderType.GOOGLE_DRIVE
            "/dropbox" -> CloudProviderType.DROPBOX
            else -> return@withContext null
        }
        uri.getQueryParameter("error")?.let { return@withContext "Sign-in was cancelled." }
        val code = uri.getQueryParameter("code")
            ?: return@withContext "${provider.label} did not sign you in."
        val flow = pending.remove(provider)
            ?: return@withContext "That sign-in request expired. Start again from Settings."
        val clientId = store.clientId(provider)
            ?: return@withContext "Missing ${provider.label} client ID. Add it in Settings first."
        try {
            val tokens = when (provider) {
                CloudProviderType.GOOGLE_DRIVE -> DriveOAuth.exchangeCode(client, clientId, code, flow.verifier)
                CloudProviderType.DROPBOX -> DropboxOAuth.exchangeCode(client, clientId, code, flow.verifier)
                CloudProviderType.WEBDAV -> error("WebDAV has no OAuth flow")
            }
            store.saveTokens(provider, flow.accountName, tokens)
            "Signed in to ${provider.label}."
        } catch (e: Exception) {
            "Sign-in failed: ${e.message}"
        }
    }
}
