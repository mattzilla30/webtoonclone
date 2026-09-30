package com.webtoonclone.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
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

        // Search paging: page 1 must add series that page 0 did not have.
        val first = repository.browse(title = "love", page = 0)
        val second = repository.browse(title = "love", page = 1)
        assertTrue("page 1 is empty", second.isNotEmpty())
        assertTrue("page 1 repeats page 0", second.none { s -> first.any { it.id == s.id } })
    }
}
