package com.webtoonclone.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

private const val API = "https://api.mangadex.org"
private const val PAGE_SIZE = 24
private const val CHAPTER_PAGE = 500
private const val MAX_ATTEMPTS = 3
private const val RANDOM_TRIES = 5
private const val PAGE_URL_TTL_MS = 10L * 60 * 1000
private const val UPDATES_PAGE = 50
private const val SIMILAR_MINIMUM = 4

enum class Order(val param: String) {
    Popular("followedCount"),
    Newest("createdAt"),
    Updated("latestUploadedChapter"),
    TopRated("rating"),
}

class MangaDexRepository(private val client: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private var tagIndex: TagIndex? = null

    /** Set from Settings. Read on the network threads, so all three are volatile. */
    @Volatile var dataSaver = false

    @Volatile var originalTitles = false

    /** The MangaDex language code for chapters, titles, and descriptions. */
    @Volatile var language = "en"

    private val _contentVersion = MutableStateFlow(0)

    /** Goes up when a setting that changes what the lists contain (language, original titles) changes. */
    val contentVersion: StateFlow<Int> = _contentVersion

    fun applySettings(settings: Settings) {
        val contentChanged = settings.language != language || settings.originalTitles != originalTitles
        dataSaver = settings.dataSaver
        originalTitles = settings.originalTitles
        language = settings.language
        if (contentChanged) _contentVersion.value += 1
    }

    private val pageUrls = TtlCache<String, List<String>>(PAGE_URL_TTL_MS)

    suspend fun browse(
        page: Int = 0,
        order: Order = Order.Popular,
        title: String? = null,
        tag: String? = null,
        ids: List<String>? = null,
        limit: Int = PAGE_SIZE,
        withStats: Boolean = false,
        filters: SearchFilters = SearchFilters(),
        authorId: String? = null,
    ): List<SeriesSummary> {
        val included = filters.included + listOfNotNull(tag)
        // The tag list is only fetched when a tag is involved.
        val index = if (included.isNotEmpty() || filters.excluded.isNotEmpty()) tagIndex() else null
        val includedIds = included.mapNotNull { index?.ids?.get(it.lowercase()) }
        val excludedIds = filters.excluded.mapNotNull { index?.ids?.get(it.lowercase()) }
        val url = "$API/manga".toHttpUrl().newBuilder()
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", (page * limit).toString())
            .addQueryParameter("hasAvailableChapters", "true")
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .addQueryParameter("availableTranslatedLanguage[]", language)
            .addQueryParameter("contentRating[]", "safe")
            .addQueryParameter("contentRating[]", "suggestive")
            .apply {
                if (ids == null) addQueryParameter("order[${order.param}]", "desc")
                ids?.forEach { addQueryParameter("ids[]", it) }
                if (title != null) addQueryParameter("title", title)
                includedIds.forEach { addQueryParameter("includedTags[]", it) }
                if (includedIds.size > 1) addQueryParameter("includedTagsMode", if (filters.matchAll) "AND" else "OR")
                excludedIds.forEach { addQueryParameter("excludedTags[]", it) }
                filters.status.forEach { addQueryParameter("status[]", it) }
                filters.demographics.forEach { addQueryParameter("publicationDemographic[]", it) }
                filters.originalLanguages.forEach { addQueryParameter("originalLanguage[]", it) }
                filters.year?.let { addQueryParameter("year", it.toString()) }
                if (authorId != null) addQueryParameter("authorOrArtist", authorId)
            }
            .build()
        val list = json.decodeFromString<MangaListDto>(fetch(url)).data.map { it.toSummary() }
        if (!withStats || list.isEmpty()) return list
        val stats = stats(list.map { it.id })
        return list.map { it.copy(follows = stats[it.id]?.follows) }
    }

    /**
     * A random series that has chapters in the chosen language, or null if five tries find none. The
     * endpoint picks without regard to language, so a few tries are usually enough.
     */
    suspend fun randomSeries(): SeriesSummary? {
        repeat(RANDOM_TRIES) {
            val url = "$API/manga/random".toHttpUrl().newBuilder()
                .addQueryParameter("includes[]", "cover_art")
                .addQueryParameter("includes[]", "author")
                .addQueryParameter("contentRating[]", "safe")
                .addQueryParameter("contentRating[]", "suggestive")
                .build()
            val manga = json.decodeFromString<MangaOneDto>(fetch(url)).data
            if (language in manga.attributes.availableTranslatedLanguages) return manga.toSummary()
        }
        return null
    }

    /** The newest series that already have chapters. Polled so new uploads show up. */
    suspend fun newSeries(): List<SeriesSummary> = browse(order = Order.Newest, limit = 3)

    /** The hero and picks are random on every call, so each app open looks different. */
    suspend fun home(): HomeContent = coroutineScope {
        // Two chains run together, so at most three requests are in flight. MangaDex allows about five a second.
        val newSeries = async { newSeries() }
        val pool = async { readablePicks(7) }
        // One random series leads the screen as the hero. The other six fill Today's Picks.
        val picked = pool.await()
        HomeContent(picked.firstOrNull(), newSeries.await(), picked.drop(1))
    }

    /** The newest chapter that opens in the reader, or null when the series has none. */
    suspend fun latestChapter(seriesId: String): Chapter? {
        val url = "$API/manga/$seriesId/feed".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "1")
            .addQueryParameter("includeExternalUrl", "0")
            .addQueryParameter("translatedLanguage[]", language)
            .addQueryParameter("order[readableAt]", "desc")
            .build()
        val dto = json.decodeFromString<ChapterListDto>(fetch(url)).data.firstOrNull() ?: return null
        return dto.toChapter()
    }

    /** One page of series ordered by their newest readable chapter. Repeats across pages are possible. */
    suspend fun latestUpdates(page: Int): List<UpdateEntry> {
        val url = "$API/chapter".toHttpUrl().newBuilder()
            .addQueryParameter("limit", UPDATES_PAGE.toString())
            .addQueryParameter("offset", (page * UPDATES_PAGE).toString())
            .addQueryParameter("includeExternalUrl", "0")
            .addQueryParameter("translatedLanguage[]", language)
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
            .addQueryParameter("translatedLanguage[]", language)
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
        val summary = manga.toSummary().copy(follows = stat?.follows)
        return SeriesDetail(
            summary = summary,
            status = manga.attributes.status,
            tags = manga.attributes.tags.map { it.attributes.name.pick() },
            rating = stat?.rating?.average,
            altTitles = alternateTitles(manga.attributes.title, manga.attributes.altTitles, shown = summary.title),
            year = manga.attributes.year,
            demographic = demographicLabel(manga.attributes.publicationDemographic),
            originalLanguage = manga.attributes.originalLanguage,
            links = buildLinks(manga.attributes.links),
        )
    }

    /**
     * One page of the chapter feed, newest first. [seen] carries chapter numbers across pages so a
     * chapter uploaded by several groups shows once. When groups overlap within a page, [preferredGroup]
     * picks the upload to show, and the others stay available as alternates.
     */
    suspend fun chapterPage(
        seriesId: String,
        offset: Int,
        seen: MutableSet<String>,
        preferredGroup: String? = null,
    ): ChapterPage {
        val url = "$API/manga/$seriesId/feed".toHttpUrl().newBuilder()
            .addQueryParameter("limit", CHAPTER_PAGE.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("translatedLanguage[]", language)
            .addQueryParameter("order[chapter]", "desc")
            .addQueryParameter("includes[]", "scanlation_group")
            .build()
        val body = json.decodeFromString<ChapterListDto>(fetch(url))
        val byNumber = LinkedHashMap<String, MutableList<Chapter>>()
        for (dto in body.data) {
            val chapter = dto.toChapter()
            if (chapter.number in seen) continue
            byNumber.getOrPut(chapter.number) { mutableListOf() } += chapter
        }
        seen += byNumber.keys
        val chapters = byNumber.values.map { pickUpload(it, preferredGroup) }
        val next = offset + body.data.size
        return ChapterPage(chapters, if (body.data.isEmpty() || next >= body.total) null else next)
    }

    /** Every chapter, oldest first. The reader uses it to find the previous and next chapter. */
    suspend fun allChapters(seriesId: String, preferredGroup: String? = null): List<Chapter> {
        val seen = mutableSetOf<String>()
        val all = mutableListOf<Chapter>()
        var offset: Int? = 0
        while (offset != null) {
            val page = chapterPage(seriesId, offset, seen, preferredGroup)
            all += page.chapters
            offset = page.nextOffset
        }
        return all.asReversed()
    }

    /** The series a chapter belongs to, for opening a MangaDex chapter link. */
    suspend fun seriesIdForChapter(chapterId: String): String? {
        val dto = json.decodeFromString<ChapterOneDto>(fetch("$API/chapter/$chapterId".toHttpUrl())).data
        return dto.relationships.firstOrNull { it.type == "manga" }?.id
    }

    /** Series like the one with [tags], most followed first, without the series itself. */
    suspend fun similar(seriesId: String, tags: List<String>, limit: Int = 10): List<SeriesSummary> {
        val chosen = similarTags(tags)
        if (chosen.isEmpty()) return emptyList()
        var found = browse(filters = SearchFilters(included = chosen), limit = limit + 1)
        // Two tags together can be too narrow, so fall back to the first alone.
        if (found.size < SIMILAR_MINIMUM && chosen.size > 1) {
            found = browse(filters = SearchFilters(included = chosen.take(1)), limit = limit + 1)
        }
        return found.filter { it.id != seriesId }.take(limit)
    }

    /**
     * Page image URLs for a chapter. They come from a server that expires after fifteen minutes, so
     * they are reused for ten. That lets the reader preload the next chapter's pages and then open
     * that chapter with the same addresses. [forceRefresh] asks for new ones, for a retry.
     */
    suspend fun pages(chapterId: String, forceRefresh: Boolean = false): List<String> {
        // The two image sets have different addresses, so the saver choice is part of the key.
        val saver = dataSaver
        val key = "$chapterId:${if (saver) "saver" else "full"}"
        if (!forceRefresh) pageUrls.get(key)?.let { return it }
        val body = fetch("$API/at-home/server/$chapterId".toHttpUrl())
        val home = json.decodeFromString<AtHomeDto>(body)
        val chapter = home.chapter
        val urls = if (saver && chapter.dataSaver.isNotEmpty()) {
            chapter.dataSaver.map { "${home.baseUrl}/data-saver/${chapter.hash}/$it" }
        } else {
            chapter.data.map { "${home.baseUrl}/data/${chapter.hash}/$it" }
        }
        pageUrls.put(key, urls)
        return urls
    }

    class TagIndex(
        /** Tag ids by lowercase name, across every group. */
        val ids: Map<String, String>,
        /** Tag names by group: genre, theme, format, or content. */
        val namesByGroup: Map<String, List<String>>,
    )

    /** Every MangaDex tag. Loaded once. */
    suspend fun tagIndex(): TagIndex {
        tagIndex?.let { return it }
        val tags = json.decodeFromString<TagListDto>(fetch("$API/manga/tag".toHttpUrl())).data
        return TagIndex(
            ids = tags.associate { it.attributes.name.pick().lowercase() to it.id },
            namesByGroup = tags.groupBy({ it.attributes.group }, { it.attributes.name.pick() }),
        ).also { tagIndex = it }
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
            authorId = relationships.firstOrNull { it.type == "author" }?.id,
            description = attributes.description.pick(),
        )
    }

    /**
     * MangaDex stores the romanized original name as the main title and translated names in
     * altTitles. Works not originally in the chosen language use its first alternate title.
     * A work with none keeps its main title.
     */
    private fun MangaDto.displayTitle(): String =
        chooseTitle(attributes.title, attributes.altTitles, attributes.originalLanguage, originalTitles, language)

    private fun Map<String, String>.pick(): String = this[language] ?: values.firstOrNull().orEmpty()

    private fun ChapterDto.toChapter() = Chapter(
        id = id,
        number = attributes.chapter ?: "Oneshot",
        title = attributes.title.orEmpty(),
        publishedAt = attributes.publishAt,
        externalUrl = attributes.externalUrl,
        group = relationships.firstOrNull { it.type == "scanlation_group" }?.attributes?.name,
        volume = attributes.volume,
    )
}
