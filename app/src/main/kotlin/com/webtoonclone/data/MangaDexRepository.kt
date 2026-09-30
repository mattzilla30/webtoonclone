package com.webtoonclone.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

private const val API = "https://api.mangadex.org"
private const val PAGE_SIZE = 24
private const val LANG = "en"

class MangaDexRepository(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun popular(page: Int): List<SeriesSummary> =
        listManga(page) { addQueryParameter("order[followedCount]", "desc") }

    suspend fun search(query: String, page: Int): List<SeriesSummary> =
        listManga(page) { addQueryParameter("title", query) }

    suspend fun series(id: String): SeriesDetail {
        val url = "$API/manga/$id".toHttpUrl().newBuilder()
            .addQueryParameter("includes[]", "cover_art")
            .build()
        val manga = json.decodeFromString<MangaOneDto>(fetch(url)).data
        return SeriesDetail(
            summary = manga.toSummary(),
            description = manga.attributes.description.pick(),
            status = manga.attributes.status,
            tags = manga.attributes.tags.map { it.attributes.name.pick() },
        )
    }

    suspend fun chapters(seriesId: String): List<Chapter> {
        val url = "$API/manga/$seriesId/feed".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "500")
            .addQueryParameter("includeExternalUrl", "0")
            .addQueryParameter("translatedLanguage[]", LANG)
            .addQueryParameter("order[chapter]", "asc")
            .build()
        val feed = json.decodeFromString<ChapterListDto>(fetch(url)).data

        // Several groups often upload the same chapter. Keep the first of each number.
        val seen = mutableSetOf<String>()
        return feed.mapNotNull { dto ->
            val number = dto.attributes.chapter ?: "Oneshot"
            if (!seen.add(number)) return@mapNotNull null
            Chapter(dto.id, number, dto.attributes.title.orEmpty(), dto.attributes.publishAt)
        }
    }

    suspend fun pages(chapterId: String): List<String> {
        val body = fetch("$API/at-home/server/$chapterId".toHttpUrl())
        val home = json.decodeFromString<AtHomeDto>(body)
        return home.chapter.data.map { "${home.baseUrl}/data/${home.chapter.hash}/$it" }
    }

    private suspend fun listManga(
        page: Int,
        extra: HttpUrl.Builder.() -> Unit,
    ): List<SeriesSummary> {
        val url = "$API/manga".toHttpUrl().newBuilder()
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("offset", (page * PAGE_SIZE).toString())
            .addQueryParameter("hasAvailableChapters", "true")
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("availableTranslatedLanguage[]", LANG)
            .addQueryParameter("contentRating[]", "safe")
            .addQueryParameter("contentRating[]", "suggestive")
            .apply(extra)
            .build()
        return json.decodeFromString<MangaListDto>(fetch(url)).data.map { it.toSummary() }
    }

    private suspend fun fetch(url: HttpUrl): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", "webtoonclone-android/0.1").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("MangaDex ${url.encodedPath} failed: ${response.code}")
            response.body.string()
        }
    }

    private fun MangaDto.toSummary(): SeriesSummary {
        val file = relationships.firstOrNull { it.type == "cover_art" }?.attributes?.fileName
        return SeriesSummary(
            id = id,
            title = attributes.title.pick(),
            coverUrl = file?.let { "https://uploads.mangadex.org/covers/$id/$it.512.jpg" },
        )
    }

    private fun Map<String, String>.pick(): String = this[LANG] ?: values.firstOrNull().orEmpty()
}
