package com.dexter.data

import android.net.Uri
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.Base64

/** The address AniList and MyAnimeList send you back to after signing in. Register these with your apps there. */
const val ANILIST_REDIRECT = "dexter://anilist"
const val MAL_REDIRECT = "dexter://mal"

private const val ANILIST_API = "https://graphql.anilist.co"
private const val MAL_API = "https://api.myanimelist.net/v2"
private const val MAL_TOKEN_URL = "https://myanimelist.net/v1/oauth2/token"
private const val MAL_REFRESH_MARGIN_MS = 24L * 60 * 60 * 1000

/** The AniList and MyAnimeList ids MangaDex lists for a series, either of which may be missing. */
fun trackerIds(links: List<SeriesLink>): Pair<Int?, Int?> {
    fun idAfter(prefix: String) = links.firstOrNull { it.url.startsWith(prefix) }?.url?.removePrefix(prefix)?.substringBefore('/')?.toIntOrNull()
    return idAfter("https://anilist.co/manga/") to idAfter("https://myanimelist.net/manga/")
}

/** A chapter number as a tracker counts it: whole chapters read. "12.5" counts as 12. A oneshot counts as 1. */
fun trackerProgress(number: String?): Int? = when {
    number == null -> null
    number.equals("Oneshot", ignoreCase = true) -> 1
    else -> number.toDoubleOrNull()?.toInt()?.takeIf { it > 0 }
}

/** The access token in an AniList redirect, which comes in the address after "#". */
fun aniListToken(fragment: String?): Pair<String, Long>? {
    val params = fragment.orEmpty().split('&').mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to URLDecoder.decode(it[1], Charsets.UTF_8) } }.toMap()
    val token = params["access_token"] ?: return null
    return token to (params["expires_in"]?.toLongOrNull() ?: 0L)
}

/** A PKCE verifier: 64 random URL-safe characters. MyAnimeList takes it as its own challenge. */
fun newVerifier(random: SecureRandom = SecureRandom()): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(48).also(random::nextBytes))

/**
 * AniList and MyAnimeList tracking through apps you register on those sites. Each chapter you open moves
 * the series' progress there forward, never back.
 */
class Trackers(
    private val store: AccountStore,
    client: OkHttpClient,
    private val repository: MangaDexRepository,
) {
    private val http = MangaDexHttp(client)

    /** The page that asks you to let Dexter use your AniList account. */
    suspend fun aniListSignInUrl(clientId: String): String {
        store.update { it.copy(aniListPendingClientId = clientId.trim()) }
        return "https://anilist.co/api/v2/oauth/authorize?client_id=${Uri.encode(clientId.trim())}&response_type=token"
    }

    /** The page that asks you to let Dexter use your MyAnimeList account. */
    suspend fun malSignInUrl(clientId: String): String {
        val verifier = newVerifier()
        store.update { it.copy(malPendingClientId = clientId.trim(), malVerifier = verifier) }
        return "https://myanimelist.net/v1/oauth2/authorize?response_type=code&client_id=${Uri.encode(clientId.trim())}" +
            "&code_challenge=$verifier&code_challenge_method=plain&redirect_uri=${Uri.encode(MAL_REDIRECT)}"
    }

    /** Finishes a sign-in when the browser sends you back. Returns a message to show, or null for another address. */
    suspend fun handleRedirect(uri: Uri): String? = when ("${uri.scheme}://${uri.host}") {
        ANILIST_REDIRECT -> {
            val pending = store.current().aniListPendingClientId
            val (token, expiresIn) = aniListToken(uri.fragment) ?: return "AniList did not sign you in."
            val name = aniListViewer(token)
            store.update {
                it.copy(
                    aniList = TrackerLogin(pending.orEmpty(), token, name, if (expiresIn > 0) System.currentTimeMillis() + expiresIn * 1000 else 0),
                    aniListPendingClientId = null,
                )
            }
            "Signed in to AniList as $name"
        }
        MAL_REDIRECT -> {
            val accounts = store.current()
            val code = uri.getQueryParameter("code")
            val clientId = accounts.malPendingClientId
            val verifier = accounts.malVerifier
            if (code == null || clientId == null || verifier == null) {
                "MyAnimeList did not sign you in."
            } else {
                val token = malToken(
                    FormBody.Builder().add("client_id", clientId).add("grant_type", "authorization_code").add("code", code)
                        .add("code_verifier", verifier).add("redirect_uri", MAL_REDIRECT).build(),
                )
                val login = TrackerLogin(clientId, token.accessToken, "", System.currentTimeMillis() + token.expiresIn * 1000, token.refreshToken)
                val name = runCatching { malViewer(login.accessToken) }.getOrDefault("")
                store.update { it.copy(myAnimeList = login.copy(userName = name), malPendingClientId = null, malVerifier = null) }
                "Signed in to MyAnimeList" + (if (name.isNotBlank()) " as $name" else "")
            }
        }
        else -> null
    }

    suspend fun signOutAniList() = store.update { it.copy(aniList = null) }

    suspend fun signOutMal() = store.update { it.copy(myAnimeList = null) }

    private suspend fun aniList(token: String, query: String, variables: JsonObject = JsonObject(emptyMap())): JsonObject {
        val body = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(ANILIST_API).header("Authorization", "Bearer $token").header("Accept", "application/json").post(body).build()
        return StoredJson.parseToJsonElement(http.send(request) { it.readUtf8() }).jsonObject
    }

    private suspend fun aniListViewer(token: String): String =
        aniList(token, "query { Viewer { name } }")["data"]?.jsonObject?.get("Viewer")?.jsonObject?.get("name")?.jsonPrimitive?.content.orEmpty()

    private suspend fun malToken(form: FormBody): TokenDto =
        http.send(Request.Builder().url(MAL_TOKEN_URL).post(form).build()) { StoredJson.decodeFromString(TokenDto.serializer(), it.readUtf8()) }

    private suspend fun malViewer(token: String): String {
        val request = Request.Builder().url("$MAL_API/users/@me").header("Authorization", "Bearer $token").build()
        return StoredJson.parseToJsonElement(http.send(request) { it.readUtf8() }).jsonObject["name"]?.jsonPrimitive?.content.orEmpty()
    }

    /** A MyAnimeList token that works for at least another day, refreshed when it is closer to expiring. */
    private suspend fun malBearer(): String? {
        val login = store.current().myAnimeList ?: return null
        if (login.expiresAt == 0L || System.currentTimeMillis() < login.expiresAt - MAL_REFRESH_MARGIN_MS) return login.accessToken
        val refresh = login.refreshToken ?: return login.accessToken
        val token = malToken(FormBody.Builder().add("client_id", login.clientId).add("grant_type", "refresh_token").add("refresh_token", refresh).build())
        val fresh = login.copy(accessToken = token.accessToken, refreshToken = token.refreshToken ?: refresh, expiresAt = System.currentTimeMillis() + token.expiresIn * 1000)
        store.update { it.copy(myAnimeList = fresh) }
        return fresh.accessToken
    }

    /** The tracker ids of [seriesId], from the saved ones or from MangaDex, which are then saved. */
    private suspend fun idsFor(seriesId: String): Pair<Int?, Int?> {
        val accounts = store.current()
        if (seriesId in accounts.aniListIds || seriesId in accounts.malIds) return accounts.aniListIds[seriesId] to accounts.malIds[seriesId]
        val (al, mal) = trackerIds(repository.series(seriesId).links)
        store.update { saved ->
            saved.copy(
                aniListIds = if (al != null) saved.aniListIds + (seriesId to al) else saved.aniListIds,
                malIds = if (mal != null) saved.malIds + (seriesId to mal) else saved.malIds,
            )
        }
        return al to mal
    }

    /**
     * Moves the series' progress on each signed-in tracker up to [chapterNumber], and marks it as being read.
     * A tracker already further along stays where it is. Series MangaDex does not link to a tracker are skipped.
     */
    suspend fun pushProgress(seriesId: String, chapterNumber: String?) {
        val accounts = store.current()
        if (accounts.aniList == null && accounts.myAnimeList == null) return
        val progress = trackerProgress(chapterNumber) ?: return
        val (al, mal) = idsFor(seriesId)
        accounts.aniList?.let { login -> if (al != null) runCatching { pushAniList(login.accessToken, al, progress) } }
        if (mal != null) runCatching { pushMal(mal, progress) }
    }

    private suspend fun pushAniList(token: String, mediaId: Int, progress: Int) {
        val vars = buildJsonObject { put("id", mediaId) }
        val entry = aniList(token, "query (\$id: Int) { Media(id: \$id) { mediaListEntry { progress } } }", vars)
        val current = entry["data"]?.jsonObject?.get("Media")?.jsonObject?.get("mediaListEntry")
            ?.let { it as? JsonObject }?.get("progress")?.jsonPrimitive?.intOrNull ?: 0
        if (progress <= current) return
        aniList(
            token,
            "mutation (\$id: Int, \$progress: Int) { SaveMediaListEntry(mediaId: \$id, progress: \$progress, status: CURRENT) { id } }",
            buildJsonObject {
                put("id", mediaId)
                put("progress", progress)
            },
        )
    }

    private suspend fun pushMal(mediaId: Int, progress: Int) {
        val token = malBearer() ?: return
        val status = Request.Builder().url("$MAL_API/manga/$mediaId?fields=my_list_status").header("Authorization", "Bearer $token").build()
        val current = StoredJson.parseToJsonElement(http.send(status) { it.readUtf8() }).jsonObject["my_list_status"]
            ?.let { it as? JsonObject }?.get("num_chapters_read")?.jsonPrimitive?.intOrNull ?: 0
        if (progress <= current) return
        val update = Request.Builder().url("$MAL_API/manga/$mediaId/my_list_status").header("Authorization", "Bearer $token")
            .patch(FormBody.Builder().add("num_chapters_read", progress.toString()).add("status", "reading").build())
            .build()
        http.send(update) { }
    }
}
