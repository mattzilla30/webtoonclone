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
        assertTrue("popular list is empty", home.popular.isNotEmpty())
        assertTrue("picks are empty", home.picks.isNotEmpty())
        assertTrue("no genre band has series", home.genreBands.any { it.series.isNotEmpty() })
        println("hero=${home.hero?.title} genre=${home.hero?.genre} author=${home.hero?.author} follows=${home.hero?.follows}")

        // Popular series are often licensed and only link out, so look for one with readable chapters.
        val candidates = home.popular + home.picks + home.genreBands.flatMap { it.series }
        val readable = candidates.firstNotNullOf { series ->
            val list = repository.chapters(series.id)
            list.firstOrNull { it.externalUrl == null }?.let { series to list.filter { c -> c.externalUrl == null } }
        }
        val (series, chapters) = readable
        val detail = repository.series(series.id)
        assertTrue(detail.summary.title.isNotBlank())
        println("series=${series.title} rating=${detail.rating} status=${detail.status}")

        val pages = repository.pages(chapters.first().id)
        assertTrue("no pages", pages.isNotEmpty())
        println("chapters=${chapters.size} pages=${pages.size} first=${pages.first()}")

        assertTrue(repository.browse(genre = "Romance", limit = 3).isNotEmpty())
        assertTrue(repository.browse(title = "tower", limit = 3).isNotEmpty())
    }
}
