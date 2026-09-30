package com.webtoonclone.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.random.Random

private const val API = "https://api.mangadex.org"
private const val PAGE_SIZE = 24
private const val LANG = "en"
private const val CHAPTER_PAGE = 500
private const val MAX_ATTEMPTS = 3
private const val UPDATES_PAGE = 50

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
        ids: List<String>? = null,
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
            .apply {
                if (ids == null) addQueryParameter("order[${order.param}]", "desc")
                ids?.forEach { addQueryParameter("ids[]", it) }
                if (title != null) addQueryParameter("title", title)
                if (tagId != null) addQueryParameter("includedTags[]", tagId)
            }
            .build()
        val list = json.decodeFromString<MangaListDto>(fetch(url)).data.map { it.toSummary() }
        if (!withStats || list.isEmpty()) return list
        val stats = stats(list.map { it.id })
        return list.map { it.copy(follows = stats[it.id]?.follows) }
    }

    /** The newest series that already have chapters. Polled so new uploads show up. */
    suspend fun newSeries(): List<SeriesSummary> = browse(order = Order.Newest, limit = 3)

    /** Picks and genre bands are random on every call, so each app open looks different. */
    suspend fun home(): HomeContent {
        // MangaDex allows about five requests per second, so these run one after another.
        val newSeries = newSeries()
        // One random series leads the screen as the hero. The other six fill Today's Picks.
        val pool = readablePicks(7)
        val hero = pool.firstOrNull()
        val picks = pool.drop(1)
        val bands = Genres.shuffled().take(2).map { (genre, _, tagline) ->
            // Skip a random number of top series so the same covers do not lead every time.
            val page = Random.nextInt(0, 6)
            val series = browse(genre = genre, page = page, limit = 5).ifEmpty { browse(genre = genre, limit = 5) }
            GenreBand(genre, tagline, series)
        }
        return HomeContent(hero, newSeries, picks, bands)
    }

    /** The newest chapter that opens in the reader, or null when the series has none. */
    suspend fun latestChapter(seriesId: String): Chapter? {
        val url = "$API/manga/$seriesId/feed".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "1")
            .addQueryParameter("includeExternalUrl", "0")
            .addQueryParameter("translatedLanguage[]", LANG)
            .addQueryParameter("order[readableAt]", "desc")
            .build()
        val dto = json.decodeFromString<ChapterListDto>(fetch(url)).data.firstOrNull() ?: return null
        return Chapter(
            dto.id,
            dto.attributes.chapter ?: "Oneshot",
            dto.attributes.title.orEmpty(),
            dto.attributes.publishAt,
            dto.attributes.externalUrl,
        )
    }

    /** One page of series ordered by their newest readable chapter. Repeats across pages are possible. */
    suspend fun latestUpdates(page: Int): List<UpdateEntry> {
        val url = "$API/chapter".toHttpUrl().newBuilder()
            .addQueryParameter("limit", UPDATES_PAGE.toString())
            .addQueryParameter("offset", (page * UPDATES_PAGE).toString())
            .addQueryParameter("includeExternalUrl", "0")
            .addQueryParameter("translatedLanguage[]", LANG)
            .addQueryParameter("order[readableAt]", "desc")
            .addQueryParameter("contentRating[]", "safe")
            .addQueryParameter("contentRating[]", "suggestive")
            .build()
        val feed = json.decodeFromString<ChapterListDto>(fetch(url)).data
        // Keep the newest chapter per series, in feed order.
        val newest = LinkedHashMap<String, ChapterDto>()
        for (chapter in feed) {
            val id = chapter.relationships.firstOrNull { it.type == "manga" }?.id ?: continue
            newest.putIfAbsent(id, chapter)
        }
        if (newest.isEmpty()) return emptyList()
        val byId = browse(ids = newest.keys.toList(), limit = newest.size).associateBy { it.id }
        return newest.mapNotNull { (id, chapter) ->
            val series = byId[id] ?: return@mapNotNull null
            UpdateEntry(series, chapter.attributes.chapter ?: "Oneshot", chapter.attributes.publishAt)
        }
    }

    /**
     * Series with recent chapters that open in the reader. Many top series only link to the
     * publisher, so this reads the newest in-app chapters and looks up the series behind them.
     */
    suspend fun readablePicks(limit: Int): List<SeriesSummary> {
        val url = "$API/chapter".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "100")
            .addQueryParameter("includeExternalUrl", "0")
            .addQueryParameter("translatedLanguage[]", LANG)
            .addQueryParameter("order[readableAt]", "desc")
            .addQueryParameter("contentRating[]", "safe")
            .addQueryParameter("contentRating[]", "suggestive")
            .build()
        val feed = json.decodeFromString<ChapterListDto>(fetch(url)).data
        val ids = feed.flatMap { c -> c.relationships.filter { it.type == "manga" }.map { it.id } }
            .distinct()
            .shuffled()
            .take(limit)
        if (ids.isEmpty()) return emptyList()
        val byId = browse(ids = ids, limit = ids.size, withStats = true).associateBy { it.id }
        return ids.mapNotNull { byId[it] }
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

    /**
     * One page of the chapter feed, newest first. [seen] carries chapter numbers across pages
     * so a chapter uploaded by several groups shows once.
     */
    suspend fun chapterPage(seriesId: String, offset: Int, seen: MutableSet<String>): ChapterPage {
        val url = "$API/manga/$seriesId/feed".toHttpUrl().newBuilder()
            .addQueryParameter("limit", CHAPTER_PAGE.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("translatedLanguage[]", LANG)
            .addQueryParameter("order[chapter]", "desc")
            .build()
        val body = json.decodeFromString<ChapterListDto>(fetch(url))
        val chapters = body.data.mapNotNull { dto ->
            val number = dto.attributes.chapter ?: "Oneshot"
            if (!seen.add(number)) return@mapNotNull null
            Chapter(
                dto.id,
                number,
                dto.attributes.title.orEmpty(),
                dto.attributes.publishAt,
                dto.attributes.externalUrl,
            )
        }
        val next = offset + body.data.size
        return ChapterPage(chapters, if (body.data.isEmpty() || next >= body.total) null else next)
    }

    /** Every chapter, oldest first. The reader uses it to find the previous and next chapter. */
    suspend fun allChapters(seriesId: String): List<Chapter> {
        val seen = mutableSetOf<String>()
        val all = mutableListOf<Chapter>()
        var offset: Int? = 0
        while (offset != null) {
            val page = chapterPage(seriesId, offset, seen)
            all += page.chapters
            offset = page.nextOffset
        }
        return all.asReversed()
    }

    suspend fun pages(chapterId: String): List<String> {
        val body = fetch("$API/at-home/server/$chapterId".toHttpUrl())
        val home = json.decodeFromString<AtHomeDto>(body)
        return home.chapter.data.map { "${home.baseUrl}/data/${home.chapter.hash}/$it" }
    }

    private suspend fun genreId(name: String): String? = genreTagIds()[name.lowercase()]

    /** MangaDex genre tag ids by lowercase name. Loaded once. */
    suspend fun genreTagIds(): Map<String, String> {
        return genreIds ?: run {
            val tags = json.decodeFromString<TagListDto>(fetch("$API/manga/tag".toHttpUrl())).data
            tags.filter { it.attributes.group == "genre" }
                .associate { it.attributes.name.pick().lowercase() to it.id }
                .also { genreIds = it }
        }
    }

    private suspend fun stats(ids: List<String>): Map<String, StatDto> = try {
        val url = "$API/statistics/manga".toHttpUrl().newBuilder()
            .apply { ids.forEach { addQueryParameter("manga[]", it) } }
            .build()
        json.decodeFromString<StatsDto>(fetch(url)).statistics
    } catch (e: IOException) {
        emptyMap()
    }

    /** Retries rate limits (429) and server errors a few times, honoring Retry-After. */
    private suspend fun fetch(url: HttpUrl): String {
        var attempt = 0
        while (true) {
            try {
                return fetchOnce(url)
            } catch (e: RetryableException) {
                if (++attempt >= MAX_ATTEMPTS) {
                    throw IOException("MangaDex ${url.encodedPath} failed: HTTP ${e.code}")
                }
                delay(e.delayMs ?: (1_000L * attempt))
            }
        }
    }

    private suspend fun fetchOnce(url: HttpUrl): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", "webtoonclone-android/0.1").build()
        client.newCall(request).execute().use { response ->
            if (response.code == 429 || response.code >= 500) {
                val wait = response.header("Retry-After")?.toLongOrNull()?.times(1_000)
                throw RetryableException(response.code, wait?.coerceAtMost(10_000))
            }
            if (!response.isSuccessful) throw IOException("MangaDex ${url.encodedPath} failed: ${response.code}")
            response.body.string()
        }
    }

    private class RetryableException(val code: Int, val delayMs: Long?) : IOException()

    private fun MangaDto.toSummary(): SeriesSummary {
        val file = relationships.firstOrNull { it.type == "cover_art" }?.attributes?.fileName
        return SeriesSummary(
            id = id,
            title = displayTitle(),
            coverUrl = file?.let { "https://uploads.mangadex.org/covers/$id/$it.512.jpg" },
            genre = attributes.tags.firstOrNull { it.attributes.group == "genre" }?.attributes?.name?.pick(),
            author = relationships.firstOrNull { it.type == "author" }?.attributes?.name,
            description = attributes.description.pick(),
        )
    }

    /**
     * MangaDex stores the romanized original name as the main title and the translated
     * English name in altTitles. Non-English works use the first English alt title.
     * A work with none keeps its main title.
     */
    private fun MangaDto.displayTitle(): String {
        val main = attributes.title
        val translated = attributes.altTitles.firstNotNullOfOrNull { it[LANG] }
        return when {
            attributes.originalLanguage == LANG -> main[LANG] ?: translated ?: main.pick()
            translated != null -> translated
            else -> main.pick()
        }
    }

    private fun Map<String, String>.pick(): String = this[LANG] ?: values.firstOrNull().orEmpty()
}
