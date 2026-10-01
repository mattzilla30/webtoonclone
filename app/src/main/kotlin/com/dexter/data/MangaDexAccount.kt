package com.dexter.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

private const val AUTH_URL = "https://auth.mangadex.org/realms/mangadex/protocol/openid-connect/token"
private const val API = "https://api.mangadex.org"

/** Refresh the access token this long before MangaDex says it expires. */
private const val TOKEN_MARGIN_MS = 60_000L

@Serializable
internal data class TokenDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 900,
)

/** What a sync changes: series to subscribe to here, and series to follow on MangaDex. */
data class SyncPlan(val subscribeHere: Set<String>, val followThere: Set<String>)

/** Follows on MangaDex and subscriptions here are merged both ways. Nothing is removed on either side. */
fun syncPlan(local: Set<String>, remote: Set<String>) = SyncPlan(subscribeHere = remote - local, followThere = local - remote)

/** The rating MangaDex holds for [seriesId] in a /rating reply, or null when you have not rated it. */
fun ratingFrom(reply: JsonObject, seriesId: String): Int? =
    (reply["ratings"] as? JsonObject)?.get(seriesId)?.jsonObject?.get("rating")?.jsonPrimitive?.int

/** Thrown when an account call needs a sign-in that is missing or that MangaDex no longer accepts. */
class NotSignedInException : IllegalStateException("Not signed in to MangaDex")

/**
 * Your MangaDex account: follows, read markers, and ratings, through a personal API client you create at
 * mangadex.org under Settings, API Clients.
 */
class MangaDexAccount(
    private val store: AccountStore,
    client: OkHttpClient,
    private val repository: MangaDexRepository,
    private val library: LibraryStore,
) {
    private val http = MangaDexHttp(client)

    /** Whether you are signed in. */
    val signedIn: Flow<Boolean> = store.accounts.map { it.mangaDex != null }.distinctUntilChanged()
    private val tokenLock = Mutex()

    /** The current access token and when it expires. Kept in memory only. */
    @Volatile private var access: Pair<String, Long>? = null

    private suspend fun tokenRequest(form: FormBody): TokenDto =
        http.send(Request.Builder().url(AUTH_URL).post(form).build()) { StoredJson.decodeFromString(TokenDto.serializer(), it.readUtf8()) }

    /** Signs in with your API client and account. Returns the username on success and throws on failure. */
    suspend fun signIn(clientId: String, clientSecret: String, username: String, password: String): String {
        val token = tokenRequest(
            FormBody.Builder()
                .add("grant_type", "password")
                .add("username", username.trim())
                .add("password", password)
                .add("client_id", clientId.trim())
                .add("client_secret", clientSecret.trim())
                .build(),
        )
        val refresh = token.refreshToken ?: error("MangaDex sent no refresh token")
        access = token.accessToken to System.currentTimeMillis() + token.expiresIn * 1000
        store.update { it.copy(mangaDex = MangaDexLogin(clientId.trim(), clientSecret.trim(), username.trim(), refresh)) }
        return username.trim()
    }

    suspend fun signOut() {
        access = null
        store.update { it.copy(mangaDex = null) }
    }

    suspend fun setReadMarkers(on: Boolean) = store.update { accounts -> accounts.copy(mangaDex = accounts.mangaDex?.copy(readMarkers = on)) }

    /** A working access token, refreshed when it is close to expiring. A refused refresh signs you out. */
    private suspend fun bearer(): String = tokenLock.withLock {
        access?.let { (token, until) -> if (System.currentTimeMillis() < until - TOKEN_MARGIN_MS) return@withLock "Bearer $token" }
        val login = store.current().mangaDex ?: throw NotSignedInException()
        val token = try {
            tokenRequest(
                FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", login.refreshToken)
                    .add("client_id", login.clientId)
                    .add("client_secret", login.clientSecret)
                    .build(),
            )
        } catch (e: HttpStatusException) {
            if (e.code == 400 || e.code == 401) {
                signOut()
                throw NotSignedInException()
            }
            throw e
        }
        access = token.accessToken to System.currentTimeMillis() + token.expiresIn * 1000
        token.refreshToken?.let { fresh -> store.update { it.copy(mangaDex = it.mangaDex?.copy(refreshToken = fresh)) } }
        "Bearer ${token.accessToken}"
    }

    private suspend fun call(method: String, path: String, body: RequestBody? = null): String {
        val request = Request.Builder().url("$API$path").header("Authorization", bearer()).method(method, body).build()
        return http.send(request) { it.readUtf8() }
    }

    private fun jsonBody(text: String) = text.toRequestBody("application/json".toMediaType())

    suspend fun follow(seriesId: String, on: Boolean) {
        if (store.current().mangaDex == null) return
        call(if (on) "POST" else "DELETE", "/manga/$seriesId/follow", if (on) jsonBody("{}") else null)
    }

    /** Marks [chapterIds] of [seriesId] read on MangaDex, when you are signed in with read markers on. */
    suspend fun markRead(seriesId: String, chapterIds: List<String>) {
        val login = store.current().mangaDex ?: return
        if (!login.readMarkers || chapterIds.isEmpty()) return
        val ids = chapterIds.joinToString(",") { "\"$it\"" }
        call("POST", "/manga/$seriesId/read", jsonBody("""{"chapterIdsRead":[$ids],"chapterIdsUnread":[]}"""))
    }

    /** Your rating of [seriesId] from 1 to 10, or null when you have not rated it or are signed out. */
    suspend fun rating(seriesId: String): Int? {
        if (store.current().mangaDex == null) return null
        val reply = StoredJson.parseToJsonElement(call("GET", "/rating?manga[]=$seriesId")).jsonObject
        return ratingFrom(reply, seriesId)
    }

    /** Rates [seriesId] from 1 to 10, or removes the rating when [rating] is null. */
    suspend fun setRating(seriesId: String, rating: Int?) {
        if (rating == null) call("DELETE", "/rating/$seriesId") else call("POST", "/rating/$seriesId", jsonBody("""{"rating":${rating.coerceIn(1, 10)}}"""))
    }

    /**
     * Merges follows and subscriptions: series you follow on MangaDex are subscribed here, and series you
     * subscribe to here are followed there. Returns how many changed on each side.
     */
    suspend fun sync(): Pair<Int, Int> {
        val remote = repository.followed(bearer())
        val local = library.current().subscribed
        val plan = syncPlan(local.mapTo(HashSet()) { it.id }, remote.mapTo(HashSet()) { it.id })
        remote.filter { it.id in plan.subscribeHere }.forEach { series ->
            library.toggleSubscribed(repository.subscriptionStart(series.id, series.title, series.coverUrl))
        }
        plan.followThere.forEach { id -> runCatching { follow(id, true) } }
        return plan.subscribeHere.size to plan.followThere.size
    }
}
