package com.dexter.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Which cloud storage a [CloudAccount] points at. */
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
        val key = "${account.type.name}_${account.baseUrl}_${entry.path}".hashCode().toString(16)
        val ext = entry.name.substringAfterLast('.', "cbz")
        val out = dir.resolve("$key.$ext")
        if (!out.exists() || out.length() == 0L) {
            open(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
        }
        out
    }

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
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
                    "response" -> {
                        href = null
                        isDir = false
                        size = 0L
                    }
                    "href" -> href = parser.nextText()
                    "collection" -> isDir = true
                    "getcontentlength" -> size = parser.nextText().toLongOrNull() ?: 0L
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
 * Google Drive access. STUB: listing and streaming need OAuth 2.0 (the Drive REST API), which the
 * app does not wire up yet. TODO: run the OAuth consent flow, keep the refresh token in the
 * encrypted vault, and implement [CloudFileProvider] against
 * https://www.googleapis.com/drive/v3/files with alt=media downloads.
 */
class GoogleDriveProvider(override val account: CloudAccount) : CloudFileProvider {
    override suspend fun list(dirPath: String): List<CloudEntry> =
        throw UnsupportedOperationException("Google Drive needs OAuth first (see class KDoc)")

    override suspend fun open(entry: CloudEntry): InputStream =
        throw UnsupportedOperationException("Google Drive needs OAuth first (see class KDoc)")
}

/**
 * Dropbox access. STUB: listing and streaming need an OAuth 2.0 access token for the Dropbox API.
 * TODO: run the OAuth PKCE flow, keep the token in the encrypted vault, and implement
 * [CloudFileProvider] against /2/files/list_folder and /2/files/download.
 */
class DropboxProvider(override val account: CloudAccount) : CloudFileProvider {
    override suspend fun list(dirPath: String): List<CloudEntry> =
        throw UnsupportedOperationException("Dropbox needs OAuth first (see class KDoc)")

    override suspend fun open(entry: CloudEntry): InputStream =
        throw UnsupportedOperationException("Dropbox needs OAuth first (see class KDoc)")
}

/** Builds the provider for an account. WebDAV works today; Drive and Dropbox throw until OAuth lands. */
fun cloudProviderFor(account: CloudAccount, client: OkHttpClient): CloudFileProvider = when (account.type) {
    CloudProviderType.WEBDAV -> WebDavProvider(account, client)
    CloudProviderType.GOOGLE_DRIVE -> GoogleDriveProvider(account)
    CloudProviderType.DROPBOX -> DropboxProvider(account)
}
