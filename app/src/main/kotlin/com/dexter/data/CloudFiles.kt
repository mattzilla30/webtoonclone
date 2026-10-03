package com.dexter.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Which cloud storage a [CloudAccount] points at. */
@Serializable
enum class CloudProviderType(val label: String) {
    WEBDAV("WebDAV"),
    GOOGLE_DRIVE("Google Drive"),
    DROPBOX("Dropbox"),
}

/**
 * Connection details for one cloud account. The password lives only in memory: the settings UI
 * collects it per session and never persists it, so a stolen backup cannot leak cloud credentials.
 */
data class CloudAccount(
    val name: String,
    val type: CloudProviderType,
    /** WebDAV: the base URL of the collection, e.g. https://nas.local/remote.php/dav/files/user/Manga */
    val baseUrl: String,
    val username: String = "",
    val password: String = "",
)

/** One file or folder on a cloud provider. [path] is provider-relative, e.g. "Series/ch1.cbz". */
data class CloudEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val modifiedAt: Long = 0,
)

/**
 * Reads CBZ/CBR files straight off a cloud provider, no manual downloading first. Implementations
 * stream file bytes; the reader caches whole archives under the app cache dir and reads pages from
 * there with [java.util.zip.ZipFile].
 */
interface CloudFileProvider {
    val account: CloudAccount

    /** Lists [dirPath] ("" for the base). Providers return entries with provider-relative paths. */
    suspend fun list(dirPath: String = ""): List<CloudEntry>

    /** Opens [entry] for reading. The caller closes the stream. */
    suspend fun open(entry: CloudEntry): InputStream

    /**
     * True when the provider knows the file is a readable comic archive (CBZ/ZIP). CBR is listed
     * but not readable: no RAR decoder ships with the app.
     */
    fun isReadableArchive(entry: CloudEntry): Boolean =
        !entry.isDirectory && entry.name.substringAfterLast('.', "").lowercase() in setOf("cbz", "zip")
}

/**
 * Downloads a cloud archive to the app cache and returns the local file, keyed by the account and
 * path so repeat opens reuse it. True streaming page reads would need HTTP range support per page;
 * caching the file once keeps every provider uniform and lets the existing ZIP page reader work.
 */
suspend fun CloudFileProvider.cachedArchive(entry: CloudEntry, cacheDir: File): File =
    withContext(Dispatchers.IO) {
        val dir = File(cacheDir, "cloud").also { it.mkdirs() }
        val baseKey = "${account.type.name}_${account.baseUrl}_${entry.path}".hashCode().toString(16)
        // Size and modification time join the key, so an updated remote file is re-fetched
        // instead of serving the stale cached copy.
        val key = "${baseKey}_${entry.size}_${entry.modifiedAt}"
        val ext = entry.name.substringAfterLast('.', "cbz")
        val out = dir.resolve("$key.$ext")
        if (!out.exists() || out.length() == 0L) {
            // Drop cached copies of older revisions of the same file (including the pre-revision key format).
            dir.listFiles { file -> file.name.startsWith(baseKey) && file.name != out.name }
                ?.forEach { it.delete() }
            open(entry).use { input -> out.writeAtomically { input.copyTo(it) } }
        }
        out
    }

/**
 * Calls [build] with the current access token, and retries once with a refreshed token on 401.
 * The caller closes the returned response. Throws [IOException] when there is no token, the
 * refresh fails, or the retry is still unauthorized.
 */
internal suspend fun OkHttpClient.callWithBearer(
    label: String,
    tokens: suspend () -> OAuthTokens?,
    onTokenRefresh: suspend () -> OAuthTokens?,
    build: (accessToken: String) -> Request,
): Response = withContext(Dispatchers.IO) {
    var access = tokens()?.accessToken ?: throw IOException("$label is not signed in.")
    var response = newCall(build(access)).execute()
    if (response.code == 401) {
        response.close()
        access = onTokenRefresh()?.accessToken
            ?: throw IOException("$label sign-in expired. Sign in again from Settings.")
        response = newCall(build(access)).execute()
    }
    response
}

/** Parses an ISO-8601 instant ("2024-01-01T00:00:00.000Z") to epoch millis, or 0 when unparseable. */
internal fun parseCloudInstant(value: String?): Long = runCatching {
    java.time.Instant.parse(value).toEpochMilli()
}.getOrDefault(0L)

/**
 * Parses an HTTP date ("Wed, 21 Oct 2015 07:28:00 GMT", as WebDAV's getlastmodified)
 * to epoch millis, or 0 when unparseable.
 */
internal fun parseHttpDate(value: String?): Long = runCatching {
    java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
        .toInstant().toEpochMilli()
}.getOrDefault(0L)

/**
 * The string at [key], or null when missing or an explicit JSON null. (`?.jsonPrimitive`
 * alone does not guard JsonNull: it is a JsonPrimitive whose content access throws.)
 */
internal fun JsonObject.stringOrNull(key: String): String? =
    get(key)?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

/** The long at [key], or null when missing, null, or not a number. */
internal fun JsonObject.longOrNull(key: String): Long? =
    get(key)?.takeIf { it !is JsonNull }?.jsonPrimitive?.longOrNull

/** The boolean at [key], or null when missing, null, or not a boolean. */
internal fun JsonObject.booleanOrNull(key: String): Boolean? =
    get(key)?.takeIf { it !is JsonNull }?.jsonPrimitive?.booleanOrNull

/**
 * A WebDAV provider over plain OkHttp: PROPFIND for listings, GET for file bytes. Works against
 * Nextcloud, ownCloud, nginx, Apache, and NAS WebDAV shares with basic auth.
 */
class WebDavProvider(
    override val account: CloudAccount,
    private val client: OkHttpClient,
) : CloudFileProvider {
    init {
        require(account.type == CloudProviderType.WEBDAV) { "WebDavProvider needs a WEBDAV account" }
        require(account.baseUrl.startsWith("http")) { "WebDAV base URL must start with http(s)" }
    }

    private fun authed(url: String, builder: Request.Builder.() -> Unit = {}): Request {
        val b = Request.Builder().url(url).apply(builder)
        if (account.username.isNotEmpty()) {
            b.header("Authorization", Credentials.basic(account.username, account.password))
        }
        return b.build()
    }

    private fun entryUrl(path: String): String {
        val base = account.baseUrl.trimEnd('/')
        val encoded = path.split('/').filter { it.isNotEmpty() }
            .joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        return if (encoded.isEmpty()) "$base/" else "$base/$encoded"
    }

    override suspend fun list(dirPath: String): List<CloudEntry> = withContext(Dispatchers.IO) {
        val body = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/></d:prop></d:propfind>"""
            .toRequestBody("application/xml".toMediaType())
        val request = authed(entryUrl(dirPath)) {
            method("PROPFIND", body)
            header("Depth", "1")
        }
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("WebDAV list failed: ${response.code}")
            val xml = response.body?.string() ?: throw IOException("Empty WebDAV response")
            parseMultistatus(xml, dirPath)
        }
    }

    override suspend fun open(entry: CloudEntry): InputStream = withContext(Dispatchers.IO) {
        val request = authed(entryUrl(entry.path)) { get() }
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("WebDAV download failed: ${response.code}")
        }
        // The caller closes the stream; closing it releases the response.
        response.body?.byteStream() ?: throw IOException("Empty WebDAV body")
    }

    private fun parseMultistatus(xml: String, dirPath: String): List<CloudEntry> {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setInput(xml.reader())
        // The path part of the base URL, e.g. "/remote.php/dav/files/user/Manga", stripped from
        // each href to get provider-relative paths.
        val basePath = runCatching { java.net.URI(account.baseUrl).path.orEmpty() }.getOrDefault("").trimEnd('/')
        val entries = mutableListOf<CloudEntry>()
        var href: String? = null
        var isDir = false
        var size = 0L
        var modifiedAt = 0L
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
                    "response" -> {
                        href = null
                        isDir = false
                        size = 0L
                        modifiedAt = 0L
                    }
                    "href" -> href = parser.nextText()
                    "collection" -> isDir = true
                    "getcontentlength" -> size = parser.nextText().toLongOrNull() ?: 0L
                    "getlastmodified" -> modifiedAt = parseHttpDate(parser.nextText())
                }
                XmlPullParser.END_TAG -> if (parser.name.substringAfter(':') == "response") {
                    val decoded = try {
                        java.net.URLDecoder.decode(href.orEmpty(), "UTF-8")
                    } catch (e: Exception) {
                        href.orEmpty()
                    }
                    val fullPath = runCatching { java.net.URI(decoded).path.orEmpty() }.getOrDefault("")
                    val relative = fullPath.removePrefix(basePath).trim('/')
                    // The first response describes the collection we listed; skip it.
                    if (relative != dirPath.trim('/')) {
                        entries += CloudEntry(
                            path = relative,
                            name = relative.substringAfterLast('/'),
                            isDirectory = isDir,
                            size = size,
                            modifiedAt = modifiedAt,
                        )
                    }
                }
            }
            event = parser.next()
        }
        return entries
    }
}

/**
 * Google Drive access through the Drive v3 REST API with an OAuth 2.0 Bearer token.
 *
 * Drive is ID-addressed, not path-addressed, so a [CloudEntry.path] carries the Drive item ID
 * (the "root" folder when listing ""). Tokens come from the caller-supplied lambdas so this
 * class never touches the token store directly; [onTokenRefresh] must persist the fresh tokens
 * itself (see [cloudProviderFor]).
 */
class GoogleDriveProvider(
    override val account: CloudAccount,
    private val client: OkHttpClient,
    private val tokens: suspend () -> OAuthTokens?,
    private val onTokenRefresh: suspend () -> OAuthTokens?,
) : CloudFileProvider {
    init {
        require(account.type == CloudProviderType.GOOGLE_DRIVE) { "GoogleDriveProvider needs a GOOGLE_DRIVE account" }
    }

    private fun listRequest(accessToken: String, folderId: String, pageToken: String?): Request {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("www.googleapis.com")
            .addPathSegments("drive/v3/files")
            .addQueryParameter("q", "'$folderId' in parents and trashed = false")
            .addQueryParameter("fields", "files(id,name,mimeType,size,modifiedTime),nextPageToken")
            .addQueryParameter("pageSize", "1000")
            .addQueryParameter("orderBy", "folder,name")
            .apply { if (pageToken != null) addQueryParameter("pageToken", pageToken) }
            .build()
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
    }

    override suspend fun list(dirPath: String): List<CloudEntry> = withContext(Dispatchers.IO) {
        val folderId = dirPath.ifBlank { "root" }
        val entries = mutableListOf<CloudEntry>()
        var pageToken: String? = null
        do {
            val response = client.callWithBearer("Google Drive", tokens, onTokenRefresh) { token ->
                listRequest(token, folderId, pageToken)
            }
            response.use {
                if (!it.isSuccessful) throw IOException("Drive list failed: ${it.code}")
                val json = Json.parseToJsonElement(it.body?.string().orEmpty()).jsonObject
                for (file in json["files"]?.jsonArray.orEmpty()) {
                    val obj = file.jsonObject
                    val id = obj.stringOrNull("id") ?: continue
                    val name = obj.stringOrNull("name") ?: continue
                    val mime = obj.stringOrNull("mimeType").orEmpty()
                    entries += CloudEntry(
                        path = id,
                        name = name,
                        isDirectory = mime == "application/vnd.google-apps.folder",
                        size = obj.longOrNull("size") ?: 0L,
                        modifiedAt = parseCloudInstant(obj.stringOrNull("modifiedTime")),
                    )
                }
                pageToken = json.stringOrNull("nextPageToken")
            }
        } while (pageToken != null)
        entries
    }

    override suspend fun open(entry: CloudEntry): InputStream = withContext(Dispatchers.IO) {
        val response = client.callWithBearer("Google Drive", tokens, onTokenRefresh) { token ->
            Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/${entry.path}?alt=media")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
        }
        if (!response.isSuccessful) {
            response.close()
            throw IOException("Drive download failed: ${response.code}")
        }
        // The caller closes the stream; closing it releases the response.
        response.body?.byteStream() ?: throw IOException("Empty Drive body")
    }
}

/**
 * Dropbox access through the Dropbox API with an OAuth 2.0 Bearer token: /2/files/list_folder
 * (with cursor pagination) for listings, /2/files/download for file bytes.
 *
 * Dropbox is path-addressed, so a [CloudEntry.path] carries the lowercase Dropbox path
 * ("" is the account root). Tokens come from the caller-supplied lambdas so this class never
 * touches the token store directly; [onTokenRefresh] must persist the fresh tokens itself
 * (see [cloudProviderFor]).
 */
class DropboxProvider(
    override val account: CloudAccount,
    private val client: OkHttpClient,
    private val tokens: suspend () -> OAuthTokens?,
    private val onTokenRefresh: suspend () -> OAuthTokens?,
) : CloudFileProvider {
    init {
        require(account.type == CloudProviderType.DROPBOX) { "DropboxProvider needs a DROPBOX account" }
    }

    override suspend fun list(dirPath: String): List<CloudEntry> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<CloudEntry>()
        var cursor: String? = null
        var hasMore: Boolean
        do {
            val body = if (cursor == null) {
                buildJsonObject {
                    put("path", dirPath)
                    put("recursive", false)
                    put("include_deleted", false)
                    put("limit", 2000)
                }
            } else {
                buildJsonObject { put("cursor", cursor) }
            }
            val endpoint = if (cursor == null) "list_folder" else "list_folder/continue"
            val response = client.callWithBearer("Dropbox", tokens, onTokenRefresh) { token ->
                Request.Builder()
                    .url("https://api.dropboxapi.com/2/files/$endpoint")
                    .header("Authorization", "Bearer $token")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            }
            response.use {
                if (!it.isSuccessful) throw IOException("Dropbox list failed: ${it.code}")
                val json = Json.parseToJsonElement(it.body?.string().orEmpty()).jsonObject
                for (item in json["entries"]?.jsonArray.orEmpty()) {
                    val obj = item.jsonObject
                    val tag = obj.stringOrNull(".tag") ?: continue
                    if (tag == "deleted") continue
                    val path = obj.stringOrNull("path_lower") ?: continue
                    entries += CloudEntry(
                        path = path,
                        name = obj.stringOrNull("name") ?: path.substringAfterLast('/'),
                        isDirectory = tag == "folder",
                        size = obj.longOrNull("size") ?: 0L,
                        modifiedAt = parseCloudInstant(obj.stringOrNull("client_modified")),
                    )
                }
                cursor = json.stringOrNull("cursor")
                hasMore = json.booleanOrNull("has_more") ?: false
            }
        } while (hasMore)
        entries
    }

    override suspend fun open(entry: CloudEntry): InputStream = withContext(Dispatchers.IO) {
        val arg = buildJsonObject { put("path", entry.path) }.toString()
        val response = client.callWithBearer("Dropbox", tokens, onTokenRefresh) { token ->
            Request.Builder()
                .url("https://content.dropboxapi.com/2/files/download")
                .header("Authorization", "Bearer $token")
                .header("Dropbox-API-Arg", arg)
                .post(ByteArray(0).toRequestBody())
                .build()
        }
        if (!response.isSuccessful) {
            response.close()
            throw IOException("Dropbox download failed: ${response.code}")
        }
        // The caller closes the stream; closing it releases the response.
        response.body?.byteStream() ?: throw IOException("Empty Dropbox body")
    }
}

/** Builds the refresh callback for an OAuth provider: refresh, persist, and return fresh tokens. */
private fun oauthRefresher(
    client: OkHttpClient,
    tokenStore: CloudTokenStore,
    account: CloudAccount,
    refresh: suspend (clientId: String, refreshToken: String) -> OAuthTokens,
): suspend () -> OAuthTokens = {
    val current = tokenStore.tokens(account.type, account.name)
        ?: throw IOException("${account.type.label} is not signed in.")
    val clientId = tokenStore.clientId(account.type)
        ?: throw IOException("Missing ${account.type.label} client ID. Add it in Settings.")
    val fresh = refresh(clientId, current.refreshToken)
    tokenStore.saveTokens(account.type, account.name, fresh)
    fresh
}

/**
 * Builds the provider for an account. WebDAV connects directly; Drive and Dropbox read tokens
 * from [tokenStore] and refresh them through it on 401, so callers only pass the store.
 */
fun cloudProviderFor(
    account: CloudAccount,
    client: OkHttpClient,
    tokenStore: CloudTokenStore,
): CloudFileProvider = when (account.type) {
    CloudProviderType.WEBDAV -> WebDavProvider(account, client)
    CloudProviderType.GOOGLE_DRIVE -> GoogleDriveProvider(
        account = account,
        client = client,
        tokens = { tokenStore.tokens(account.type, account.name) },
        onTokenRefresh = oauthRefresher(client, tokenStore, account) { id, rt ->
            DriveOAuth.refresh(client, id, rt)
        },
    )
    CloudProviderType.DROPBOX -> DropboxProvider(
        account = account,
        client = client,
        tokens = { tokenStore.tokens(account.type, account.name) },
        onTokenRefresh = oauthRefresher(client, tokenStore, account) { id, rt ->
            DropboxOAuth.refresh(client, id, rt)
        },
    )
}
