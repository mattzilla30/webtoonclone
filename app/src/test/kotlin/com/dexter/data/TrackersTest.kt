package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackersTest {
    @Test
    fun idsComeFromTheLinks() {
        val links = listOf(SeriesLink("AniList", "https://anilist.co/manga/105398"), SeriesLink("MyAnimeList", "https://myanimelist.net/manga/121496"))
        assertEquals(105398 to 121496, trackerIds(links))
        assertEquals(null to null, trackerIds(emptyList()))
    }

    @Test
    fun progressCountsWholeChapters() {
        assertEquals(12, trackerProgress("12.5"))
        assertEquals(1, trackerProgress("Oneshot"))
        assertNull(trackerProgress("0"))
        assertNull(trackerProgress(null))
    }

    @Test
    fun aniListTokenComesFromTheFragment() {
        assertEquals("abc.def" to 31536000L, aniListToken("access_token=abc.def&token_type=Bearer&expires_in=31536000"))
        assertNull(aniListToken("error=access_denied"))
    }

    @Test
    fun verifierFitsMyAnimeListRules() {
        val verifier = newVerifier()
        assertEquals(64, verifier.length)
        assertTrue(verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }
}
