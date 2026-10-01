package com.dexter.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException

private const val API = "https://api.mangadex.org"
private const val PAGE_SIZE = 24
private const val CHAPTER_PAGE = 500
private const val RANDOM_TRIES = 12
private const val PAGE_URL_TTL_MS = 10L * 60 * 1000
private const val UPDATES_PAGE = 50
private const val SIMILAR_MINIMUM = 4
private const val UPLOADS_PAGE = 100
private const val CHAPTER_LIST_TTL_MS = 5L * 60 * 1000

enum class Order(val param: String) {
    Popular("followedCount"),
    Newest("createdAt"),
    Updated("latestUploadedChapter"),
    TopRated("rating"),
}

/**
 * The MangaDex API. [loadSettings] reads your saved settings. The first request waits for them, so a
 * background check in a fresh process uses your language and ratings, not the defaults.
 */
class MangaDexRepository(
    private val client: OkHttpClient,
    private val loadSettings: (suspend () -> Settings)? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = MangaDexHttp(client)
    private var tagIndex: TagIndex? = null

    /** Set from Settings. Read on the network threads, so all three are volatile. */
    @Volatile var dataSaver = false

    @Volatile var originalTitles = false

    /** The MangaDex language code for chapters, titles, and descriptions. */
    @Volatile var language = "en"

    private val _contentVersion = MutableStateFlow(0)

    /** Goes up when a setting that changes what the lists contain (language, original titles) changes. */
    val contentVersion: StateFlow<Int> = _contentVersion

    /** The content ratings to list, as MangaDex names them. Sorted so a change is easy to spot. */
    @Volatile var contentRatings: List<String> = ContentRatings

    /** Chapters you can read in the app, in your language, the most recently readable first. */
    private fun HttpUrl.Builder.newestReadable(): HttpUrl.Builder = apply {
        addQueryParameter("includeExternalUrl", "0")
        addQueryParameter("translatedLanguage[]", language)
        addQueryParameter("order[readableAt]", "desc")
    }

    private fun HttpUrl.Builder.ratings(): HttpUrl.Builder = apply { contentRatings.forEach { addQueryParameter("contentRating[]", it) } }

    /** Your blocks, read on the network threads. */
    @Volatile var blockedTags: Set<String> = emptySet()

    @Volatile var blockedGroups: Set<String> = emptySet()

    @Volatile var hiddenSeries: Set<String> = emptySet()

    @Volatile private var settingsApplied = false
    private val settingsLock = Mutex()

    /** Applies the saved settings once, before the first request. Later changes arrive through [applySettings]. */
    private suspend fun ensureSettings() {
        if (settingsApplied) return
        val load = loadSettings ?: return
        settingsLock.withLock { if (!settingsApplied) applySettings(load()) }
    }

    fun applySettings(settings: Settings) {
        val ratings = ratingsFor(settings.contentRatings)
        val contentChanged = settings.language != language || settings.originalTitles != originalTitles || ratings != contentRatings ||
            settings.blockedTags != blockedTags || settings.blockedGroups != blockedGroups || settings.hiddenSeries != hiddenSeries
        blockedTags = settings.blockedTags
        blockedGroups = settings.blockedGroups
        hiddenSeries = settings.hiddenSeries
        contentRatings = ratings
        dataSaver = settings.dataSaver
        originalTitles = settings.originalTitles
        language = settings.language
        // The first settings replace the defaults before anything loaded, so no screen needs to reload.
        if (contentChanged && settingsApplied) _contentVersion.value += 1
        settingsApplied = true
    }

    private val pageUrls = TtlCache<String, List<String>>(PAGE_URL_TTL_MS)

    /**
     * Whole chapter lists, oldest first, kept for a few minutes. The series page fills it when one page
     * holds every chapter, so the reader opens from it, and so does each next chapter after that.
     */
    private val chapterLists = TtlCache<String, List<Chapter>>(CHAPTER_LIST_TTL_MS)

    /** Everything that changes a chapter list, so a new language or block never reads an old list. */
    private fun chapterListKey(seriesId: String, preferredGroup: String?) =
        listOf(seriesId, preferredGroup, language, contentRatings, blockedGroups.sorted()).joinToString("|")

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
        ensureSettings()
        val included = filters.included + listOfNotNull(tag)
        // The tag list is only fetched when a tag is involved.
        // Blocked tags and hidden series shape lists. Looking up specific series by id skips them.
        val excluded = if (ids == null) effectiveExcluded(filters.excluded, blockedTags, included) else filters.excluded
        val index = if (included.isNotEmpty() || excluded.isNotEmpty()) tagIndex() else null
        val includedIds = included.mapNotNull { index?.ids?.get(it.lowercase()) }
        val excludedIds = excluded.mapNotNull { index?.ids?.get(it.lowercase()) }
        val url = "$API/manga".toHttpUrl().newBuilder()
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", (page * limit).toString())
            .addQueryParameter("hasAvailableChapters", "true")
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .addQueryParameter("availableTranslatedLanguage[]", language)
            .ratings()
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
        val hidden = hiddenSeries
        val list = fetchJson<MangaListDto>(url).data.map { it.toSummary() }.filter { ids != null || it.id !in hidden }
        if (!withStats || list.isEmpty()) return list
        val stats = stats(list.map { it.id })
        return list.map { it.copy(follows = stats[it.id]?.follows) }
    }

    /**
     * A random series that has chapters in the chosen language, or null if a dozen tries find none. The
     * endpoint picks without regard to language, so a few tries are usually enough.
     */
    suspend fun randomSeries(): SeriesSummary? {
        ensureSettings()
        repeat(RANDOM_TRIES) {
            val url = "$API/manga/random".toHttpUrl().newBuilder()
                .addQueryParameter("includes[]", "cover_art")
                .addQueryParameter("includes[]", "author")
                .ratings()
                .build()
            val manga = fetchJson<MangaOneDto>(url).data
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
        ensureSettings()
        val url = "$API/manga/$seriesId/feed".toHttpUrl().newBuilder()
            .ratings()
            .addQueryParameter("limit", "1")
            .newestReadable()
            .build()
        val dto = fetchJson<ChapterListDto>(url).data.firstOrNull() ?: return null
        return dto.toChapter()
    }

    /**
     * The newest upload in any language for each of [ids], by series id, at most 100 series per request.
     * The background check uses it to skip series where nothing was uploaded since its last look. Every
     * rating is asked for, so a series never goes missing because of a filter.
     */
    suspend fun latestUploads(ids: List<String>): Map<String, String?> {
        ensureSettings()
        val result = HashMap<String, String?>()
        for (chunk in ids.chunked(UPLOADS_PAGE)) {
            val url = "$API/manga".toHttpUrl().newBuilder()
                .addQueryParameter("limit", chunk.size.toString())
                .apply {
                    chunk.forEach { addQueryParameter("ids[]", it) }
                    ContentRatings.forEach { addQueryParameter("contentRating[]", it) }
                }
                .build()
            fetchJson<MangaListDto>(url).data.forEach { result[it.id] = it.attributes.latestUploadedChapter }
        }
        return result
    }

    /** One page of series ordered by their newest readable chapter. Repeats across pages are possible. */
    suspend fun latestUpdates(page: Int): List<UpdateEntry> {
        ensureSettings()
        val url = "$API/chapter".toHttpUrl().newBuilder()
            .addQueryParameter("limit", UPDATES_PAGE.toString())
            .addQueryParameter("offset", (page * UPDATES_PAGE).toString())
            .newestReadable()
            .ratings()
            .build()
        val feed = fetchJson<ChapterListDto>(url).data
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
        ensureSettings()
        val url = "$API/chapter".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "100")
            .newestReadable()
            .ratings()
            .build()
        val feed = fetchJson<ChapterListDto>(url).data
        val ids = feed.flatMap { c -> c.relationships.filter { it.type == "manga" }.map { it.id } }
            .distinct()
            .shuffled()
            .take(limit)
        if (ids.isEmpty()) return emptyList()
        val byId = browse(ids = ids, limit = ids.size, withStats = true).associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    suspend fun series(id: String): SeriesDetail {
        ensureSettings()
        val url = "$API/manga/$id".toHttpUrl().newBuilder()
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .build()
        // The statistics do not depend on the series, so both requests go out together.
        val (manga, stat) = coroutineScope {
            val manga = async { fetchJson<MangaOneDto>(url).data }
            val stat = async { statsOne(id) }
            manga.await() to stat.await()
        }
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
            relations = manga.relationships.filter { it.type == "manga" && it.related != null }.map { SeriesRelation(it.id, it.related!!) },
            ratingDistribution = ratingCounts(stat?.rating?.distribution.orEmpty()),
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
        ensureSettings()
        val wholeList = offset == 0 && seen.isEmpty()
        val key = chapterListKey(seriesId, preferredGroup)
        val url = "$API/manga/$seriesId/feed".toHttpUrl().newBuilder()
            .ratings()
            .addQueryParameter("limit", CHAPTER_PAGE.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("translatedLanguage[]", language)
            .addQueryParameter("order[chapter]", "desc")
            .addQueryParameter("includes[]", "scanlation_group")
            .build()
        val body = fetchJson<ChapterListDto>(url)
        val byNumber = LinkedHashMap<String, MutableList<Chapter>>()
        for (dto in body.data) {
            val chapter = dto.toChapter()
            if (chapter.number in seen || chapter.group in blockedGroups) continue
            byNumber.getOrPut(chapter.number) { mutableListOf() } += chapter
        }
        seen += byNumber.keys
        val chapters = byNumber.values.map { pickUpload(it, preferredGroup) }
        val next = offset + body.data.size
        val nextOffset = if (body.data.isEmpty() || next >= body.total) null else next
        if (wholeList && nextOffset == null) chapterLists.put(key, chapters.asReversed())
        return ChapterPage(chapters, nextOffset)
    }

    /**
     * Every chapter, oldest first. The reader uses it to find the previous and next chapter. [fresh]
     * skips the kept list, for a chapter newer than it.
     */
    suspend fun allChapters(seriesId: String, preferredGroup: String? = null, fresh: Boolean = false): List<Chapter> {
        ensureSettings()
        val key = chapterListKey(seriesId, preferredGroup)
        if (!fresh) chapterLists.get(key)?.let { return it }
        val seen = mutableSetOf<String>()
        val all = mutableListOf<Chapter>()
        var offset: Int? = 0
        while (offset != null) {
            val page = chapterPage(seriesId, offset, seen, preferredGroup)
            all += page.chapters
            offset = page.nextOffset
        }
        return all.asReversed().also { chapterLists.put(key, it) }
    }

    /** Summaries of related series, in the order MangaDex lists them. Series with nothing to read in your language are left out. */
    suspend fun relatedSeries(relations: List<SeriesRelation>): List<SeriesSummary> {
        if (relations.isEmpty()) return emptyList()
        val byId = browse(ids = relations.map { it.id }, limit = relations.size.coerceAtMost(PAGE_SIZE)).associateBy { it.id }
        return relations.mapNotNull { byId[it.id] }
    }

    /** Every cover of a series, volume order, at a size that suits the gallery. */
    suspend fun covers(seriesId: String): List<SeriesCover> {
        ensureSettings()
        val url = "$API/cover".toHttpUrl().newBuilder()
            .addQueryParameter("manga[]", seriesId)
            .addQueryParameter("limit", "100")
            .addQueryParameter("order[volume]", "asc")
            .build()
        return fetchJson<CoverListDto>(url).data.map {
            SeriesCover("https://uploads.mangadex.org/covers/$seriesId/${it.attributes.fileName}.512.jpg", it.attributes.volume)
        }
    }

    /** The series a chapter belongs to, for opening a MangaDex chapter link. */
    suspend fun seriesIdForChapter(chapterId: String): String? {
        ensureSettings()
        val dto = fetchJson<ChapterOneDto>("$API/chapter/$chapterId".toHttpUrl()).data
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
        ensureSettings()
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
        ensureSettings()
        tagIndex?.let { return it }
        val tags = fetchJson<TagListDto>("$API/manga/tag".toHttpUrl()).data
        return TagIndex(
            ids = tags.associate { it.attributes.name.pick().lowercase() to it.id },
            namesByGroup = tags.groupBy({ it.attributes.group }, { it.attributes.name.pick() }),
        ).also { tagIndex = it }
    }

    /** Statistics for one series. Only this form carries the score distribution. */
    private suspend fun statsOne(id: String): StatDto? = try {
        fetchJson<StatsDto>("$API/statistics/manga/$id".toHttpUrl()).statistics[id]
    } catch (e: IOException) {
        null
    }

    private suspend fun stats(ids: List<String>): Map<String, StatDto> = try {
        val url = "$API/statistics/manga".toHttpUrl().newBuilder()
            .apply { ids.forEach { addQueryParameter("manga[]", it) } }
            .build()
        fetchJson<StatsDto>(url).statistics
    } catch (e: IOException) {
        emptyMap()
    }

    private suspend fun fetch(url: HttpUrl): String = http.get(url)

    /** Fetches [url] and decodes it off the main thread, since a page of chapters or series is a lot of JSON. */
    private suspend inline fun <reified T> fetchJson(url: HttpUrl): T = withContext(Dispatchers.Default) { json.decodeFromString<T>(http.get(url)) }

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
