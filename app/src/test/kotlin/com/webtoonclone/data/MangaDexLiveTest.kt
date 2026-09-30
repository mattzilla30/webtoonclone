package com.webtoonclone.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Calls the real MangaDex API. Run with `./gradlew :app:testDebugUnitTest -Dlive=true`. */
class MangaDexLiveTest {
    private val repository = MangaDexRepository(OkHttpClient())

    @Test
    fun homeSeriesChaptersAndPagesParse() = runBlocking {
        assumeTrue(System.getProperty("live") == "true" || System.getenv("LIVE") == "true")

        val home = repository.home()
        assertTrue("picks are empty", home.picks.isNotEmpty())
        println("hero=${home.hero?.title} genre=${home.hero?.genre} author=${home.hero?.author} follows=${home.hero?.follows}")

        // Top series are often licensed and only link out, so look for one with readable chapters.
        val candidates = home.picks + home.newSeries
        val readable = candidates.firstNotNullOf { series ->
            val list = repository.allChapters(series.id)
            list.firstOrNull { it.externalUrl == null }?.let { series to list.filter { c -> c.externalUrl == null } }
        }
        val (series, chapters) = readable

        // Every pick must open in the reader, so its first chapter cannot be an external link.
        val pick = home.picks.first()
        assertTrue("pick ${pick.title} has no readable chapter", repository.allChapters(pick.id).any { it.externalUrl == null })
        val detail = repository.series(series.id)
        assertTrue(detail.summary.title.isNotBlank())
        println("series=${series.title} rating=${detail.rating} status=${detail.status}")

        val pages = repository.pages(chapters.first().id)
        assertTrue("no pages", pages.isNotEmpty())
        println("chapters=${chapters.size} pages=${pages.size} first=${pages.first()}")

        assertTrue(repository.browse(tag = "Romance", limit = 3).isNotEmpty())
        assertTrue(repository.browse(title = "tower", limit = 3).isNotEmpty())

        // English titles come from altTitles. Romanized names would be lowercase-ASCII-heavy
        // Japanese words, so check that a well-known series shows its translated name.
        val tower = repository.browse(title = "Solo Leveling", limit = 5).map { it.title }
        assertTrue("expected the English title, got $tower", tower.any { it.equals("Solo Leveling", ignoreCase = true) })

        // Chapter paging: page 2 must start where page 1 ended, without repeats.
        val seen = mutableSetOf<String>()
        val longSeries = "d7037b2a-874a-4360-8a7b-07f2899152fd" // 559 English chapters at the time of writing
        val a = repository.chapterPage(longSeries, 0, seen)
        assertTrue("expected a next page", a.nextOffset != null)
        val b = repository.chapterPage(longSeries, a.nextOffset!!, seen)
        assertTrue("page 2 is empty", b.chapters.isNotEmpty())
        assertTrue("page 2 repeats page 1", b.chapters.none { c -> a.chapters.any { it.number == c.number } })
        println("chapter pages: ${a.chapters.size} then ${b.chapters.size}, first=${a.chapters.first().number}")

        // Updates: newest readable chapters, one row per series.
        val updates = repository.latestUpdates(0)
        assertTrue("updates are empty", updates.isNotEmpty())
        assertTrue("a series repeats within one page", updates.map { it.series.id }.toSet().size == updates.size)
        println("updates=${updates.size} newest=${updates.first().series.title} ep ${updates.first().chapterNumber}")

        // The notification worker compares this against the last chapter it saw.
        val latest = repository.latestChapter(series.id)
        assertTrue("no latest chapter", latest != null && latest.externalUrl == null)
        assertTrue("latest chapter number is missing from the full list", chapters.any { it.number == latest!!.number })

        // Every tag list in the app must match MangaDex's group exactly, or a filter would
        // silently do nothing (missing) or the app would lack a tag MangaDex has (extra).
        val byGroup = repository.tagIndex().namesByGroup
        val ours = mapOf(
            "genre" to Genres.map { it.name },
            "theme" to Themes,
            "format" to Formats,
            "content" to ContentTags,
        )
        for ((group, names) in ours) {
            val live = byGroup[group].orEmpty()
            assertTrue("$group missing on MangaDex: ${names - live.toSet()}", (names - live.toSet()).isEmpty())
            assertTrue("$group has tags the app lacks: ${live - names.toSet()}", (live - names.toSet()).isEmpty())
        }

        // A theme filter must narrow results: tagged results differ from the untagged top list.
        val vampires = repository.browse(tag = "Vampires", limit = 5)
        assertTrue("no Vampires results", vampires.isNotEmpty())
        assertTrue("theme filter had no effect", vampires.map { it.id } != repository.browse(limit = 5).map { it.id })

        // Sorting: newest-first must differ from most-followed for the same search.
        val popular = repository.browse(title = "love", order = Order.Popular, limit = 5).map { it.id }
        val newest = repository.browse(title = "love", order = Order.Newest, limit = 5).map { it.id }
        assertTrue("sort had no effect", popular.isNotEmpty() && popular != newest)

        // The app's HTTP cache: a repeat API request is served locally, an at-home request never is.
        val cached = cachingClient(java.nio.file.Files.createTempDirectory("api-cache").toFile())
        fun get(url: String) = cached.newCall(okhttp3.Request.Builder().url(url).header("User-Agent", "webtoonclone-test").build())
            .execute().use { it.body.string(); it.cacheResponse != null }
        val tagUrl = "https://api.mangadex.org/manga/tag"
        assertTrue("first request should hit the network", !get(tagUrl))
        assertTrue("second request should come from the cache", get(tagUrl))
        val atHome = "https://api.mangadex.org/at-home/server/${chapters.first().id}"
        get(atHome)
        assertTrue("at-home responses must not be cached", !get(atHome))

        // Page URLs are reused for ten minutes, so the next chapter can be preloaded and then opened.
        val firstLoad = repository.pages(chapters.first().id)
        assertTrue("page URLs should be reused", firstLoad === repository.pages(chapters.first().id))
        assertTrue("a forced refresh should still return pages", repository.pages(chapters.first().id, forceRefresh = true).isNotEmpty())

        // Parsing holds up across many series. MangaDex sends [] for empty fields on some of them, and
        // a single run only sees a few, so read a spread of deep pages and several random draws.
        listOf(Order.Popular to 6, Order.Newest to 3, Order.Updated to 4, Order.TopRated to 5).forEach { (order, page) ->
            assertTrue("no series on $order page $page", repository.browse(page = page, order = order).isNotEmpty())
        }
        repeat(8) { assertTrue("random draw $it failed", repository.randomSeries() != null) }

        // Random: series with English chapters, and not the same one every time.
        val randoms = (1..3).mapNotNull { repository.randomSeries() }
        assertTrue("random found nothing", randoms.isNotEmpty())
        assertTrue("random repeated one series", randoms.size < 3 || randoms.map { it.id }.toSet().size > 1)

        // Details: a well-known series has a year, an original language, and outside links.
        val solo = repository.browse(title = "Solo Leveling", limit = 5).first { it.title.equals("Solo Leveling", ignoreCase = true) }
        val soloDetail = repository.series(solo.id)
        assertTrue("no year", soloDetail.year != null)
        assertTrue("no original language", soloDetail.originalLanguage.isNotEmpty())
        assertTrue("no AniList link: ${soloDetail.links}", soloDetail.links.any { it.label == "AniList" })
        assertTrue("alternate titles repeat the shown title", soloDetail.altTitles.none { it.equals(solo.title, ignoreCase = true) })

        // Original titles setting: the romanized name replaces the English one.
        val originalRepo = MangaDexRepository(OkHttpClient()).also { it.originalTitles = true }
        val originalTitles = originalRepo.browse(title = "Solo Leveling", limit = 5).map { it.title }
        assertTrue("expected the romanized title, got $originalTitles", originalTitles.any { it.contains("Honjaman", ignoreCase = true) })

        // Data saver: the smaller image set has its own addresses, and those images load.
        val saverRepo = MangaDexRepository(OkHttpClient()).also { it.dataSaver = true }
        val saverPages = saverRepo.pages(chapters.first().id)
        assertTrue("saver pages should use /data-saver/: ${saverPages.first()}", saverPages.all { "/data-saver/" in it })
        assertTrue("full pages should use /data/", pages.all { "/data/" in it })
        fun imageBytes(url: String) = OkHttpClient().newCall(Request.Builder().url(url).header("User-Agent", "webtoonclone-test").build())
            .execute().use { check(it.isSuccessful) { "image ${it.code}: $url" }; it.body.bytes().size }
        assertTrue("the saver image should not be larger", imageBytes(saverPages.first()) <= imageBytes(pages.first()))

        // Top rated is a different list from most followed.
        val topRated = repository.browse(order = Order.TopRated, limit = 5).map { it.id }
        assertTrue("top rated is empty", topRated.isNotEmpty())
        assertTrue("top rated matches popular", topRated != repository.browse(order = Order.Popular, limit = 5).map { it.id })

        // Language: another language returns its own series and chapters.
        val spanishRepo = MangaDexRepository(OkHttpClient()).also { it.language = "es" }
        val spanish = spanishRepo.browse(limit = 10)
        assertTrue("no Spanish series", spanish.isNotEmpty())
        // A series can be listed for a language yet have no chapters in it, so look for one that does.
        val spanishWithChapters = spanish.firstOrNull { spanishRepo.chapterPage(it.id, 0, mutableSetOf()).chapters.isNotEmpty() }
        assertTrue("no Spanish series has Spanish chapters", spanishWithChapters != null)

        // Filters: included, excluded, and status limits all apply to what comes back.
        val filtered = repository.browse(
            filters = SearchFilters(included = listOf("Romance"), excluded = listOf("Horror"), status = listOf("completed")),
            limit = 10,
        )
        assertTrue("filters returned nothing", filtered.isNotEmpty())
        val filteredDetail = repository.series(filtered.first().id)
        assertEquals("status filter ignored", "completed", filteredDetail.status)
        assertTrue("excluded tag present: ${filteredDetail.tags}", "Horror" !in filteredDetail.tags)
        assertTrue("included tag missing: ${filteredDetail.tags}", "Romance" in filteredDetail.tags)
        val year2020 = repository.browse(filters = SearchFilters(year = 2020), limit = 5)
        assertTrue("year filter returned nothing", year2020.isNotEmpty())
        assertEquals("year filter ignored", 2020, repository.series(year2020.first().id).year)

        // Authors: a series' author id leads to a list that includes that series.
        val withAuthor = repository.browse(limit = 24).first { it.authorId != null }
        val authorWorks = repository.browse(authorId = withAuthor.authorId, limit = 20)
        assertTrue("author page misses the series", authorWorks.any { it.id == withAuthor.id })

        // Similar: a well-known series has neighbours, and never lists itself.
        val soloSimilar = repository.similar(solo.id, soloDetail.tags)
        assertTrue("no similar series for ${soloDetail.tags}", soloSimilar.isNotEmpty())
        assertTrue("similar lists the series itself", soloSimilar.none { it.id == solo.id })

        // Groups: chapters name the group that uploaded them, and a chapter id leads back to its series.
        val firstChapters = repository.chapterPage(series.id, 0, mutableSetOf()).chapters
        assertTrue("no chapter names a group", firstChapters.any { it.group != null })
        assertEquals("chapter link lookup", series.id, repository.seriesIdForChapter(chapters.first().id))

        // Search paging: page 1 must add series that page 0 did not have.
        val first = repository.browse(title = "love", page = 0)
        val second = repository.browse(title = "love", page = 1)
        assertTrue("page 1 is empty", second.isNotEmpty())
        assertTrue("page 1 repeats page 0", second.none { s -> first.any { it.id == s.id } })
    }
}
