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

enum class Order(val param: String) {
    Popular("followedCount"),
    Newest("createdAt"),
    Updated("latestUploadedChapter"),
}

class MangaDexRepository(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }
    private var genreIds: Map<String, String>? = null

    suspend fun browse(
        page: Int = 0,
        order: Order = Order.Popular,
        title: String? = null,
        genre: String? = null,
        limit: Int = PAGE_SIZE,
        withStats: Boolean = false,
    ): List<SeriesSummary> {
        val tagId = genre?.let { genreId(it) }
        val url = "$API/manga".toHttpUrl().newBuilder()
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", (page * limit).toString())
            .addQueryParameter("hasAvailableChapters", "true")
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .addQueryParameter("availableTranslatedLanguage[]", LANG)
            .addQueryParameter("contentRating[]", "safe")
            .addQueryParameter("contentRating[]", "suggestive")
            .addQueryParameter("order[${order.param}]", "desc")
            .apply {
                if (title != null) addQueryParameter("title", title)
                if (tagId != null) addQueryParameter("includedTags[]", tagId)
            }
            .build()
        val list = json.decodeFromString<MangaListDto>(fetch(url)).data.map { it.toSummary() }
        if (!withStats || list.isEmpty()) return list
        val stats = stats(list.map { it.id })
        return list.map { it.copy(follows = stats[it.id]?.follows) }
    }

    suspend fun home(): HomeContent {
        // MangaDex allows about five requests per second, so these run one after another.
        val popular = browse(order = Order.Popular, limit = 6, withStats = true)
        val newSeries = browse(order = Order.Newest, limit = 3)
        val picks = browse(order = Order.Updated, limit = 6, withStats = true)
        val bands = listOf(
            "Romance" to "Love, crushes, and second chances",
            "Fantasy" to "Magic, dragons, and other worlds",
        ).map { (genre, tagline) ->
            GenreBand(genre, tagline, browse(genre = genre, limit = 5))
        }
        return HomeContent(popular.firstOrNull(), newSeries, picks, popular.take(5), bands)
    }

    suspend fun series(id: String): SeriesDetail {
        val url = "$API/manga/$id".toHttpUrl().newBuilder()
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .build()
        val manga = json.decodeFromString<MangaOneDto>(fetch(url)).data
        val stat = stats(listOf(id))[id]
        return SeriesDetail(
            summary = manga.toSummary().copy(follows = stat?.follows),
            status = manga.attributes.status,
            tags = manga.attributes.tags.map { it.attributes.name.pick() },
            rating = stat?.rating?.average,
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

    private suspend fun genreId(name: String): String? {
        val ids = genreIds ?: run {
            val tags = json.decodeFromString<TagListDto>(fetch("$API/manga/tag".toHttpUrl())).data
            tags.filter { it.attributes.group == "genre" }
                .associate { it.attributes.name.pick().lowercase() to it.id }
                .also { genreIds = it }
        }
        return ids[name.lowercase()]
    }

    private suspend fun stats(ids: List<String>): Map<String, StatDto> = try {
        val url = "$API/statistics/manga".toHttpUrl().newBuilder()
            .apply { ids.forEach { addQueryParameter("manga[]", it) } }
            .build()
        json.decodeFromString<StatsDto>(fetch(url)).statistics
    } catch (e: IOException) {
        emptyMap()
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
            genre = attributes.tags.firstOrNull { it.attributes.group == "genre" }?.attributes?.name?.pick(),
            author = relationships.firstOrNull { it.type == "author" }?.attributes?.name,
            description = attributes.description.pick(),
        )
    }

    private fun Map<String, String>.pick(): String = this[LANG] ?: values.firstOrNull().orEmpty()
}
