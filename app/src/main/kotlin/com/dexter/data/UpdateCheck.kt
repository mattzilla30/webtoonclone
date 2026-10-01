package com.dexter.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request

/** Where release builds of Dexter are published. */
const val RELEASES_API = "https://api.github.com/repos/mattzilla30/webtoonclone/releases/latest"

@Serializable
data class ReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
)

@Serializable
data class Release(
    @SerialName("tag_name") val tag: String,
    @SerialName("html_url") val page: String,
    val name: String? = null,
    val assets: List<ReleaseAsset> = emptyList(),
) {
    /** The APK attached to the release, when there is one. */
    val apk: String? get() = assets.firstOrNull { it.name.endsWith(".apk") }?.url
}

/** The numbers in a version such as "v1.2.10" or "1.2.10-beta", as 1, 2, 10. */
private fun versionParts(version: String): List<Int> =
    version.trim().removePrefix("v").removePrefix("V").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }

/** True when [tag] names a later version than [current], comparing number by number. */
fun isNewer(tag: String, current: String): Boolean {
    val a = versionParts(tag)
    val b = versionParts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

/** Asks GitHub for the latest release. Null when the repository has none, or does not let the app see it. */
suspend fun latestRelease(client: OkHttpClient): Release? {
    val request = Request.Builder().url(RELEASES_API).header("Accept", "application/vnd.github+json").build()
    return try {
        MangaDexHttp(client).send(request) { StoredJson.decodeFromString(Release.serializer(), it.readUtf8()) }
    } catch (e: HttpStatusException) {
        if (e.code == 404) null else throw e
    }
}
