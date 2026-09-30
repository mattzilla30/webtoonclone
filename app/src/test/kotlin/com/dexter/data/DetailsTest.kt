package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailsTest {
    @Test
    fun alternateTitlesSkipTheShownOneAndRepeats() {
        val main = mapOf("ko-ro" to "Na Honjaman Level-Up")
        val alts = listOf(mapOf("en" to "Solo Leveling"), mapOf("en" to "solo leveling"), mapOf("ko" to "나 혼자만 레벨업"), mapOf("en" to "  "))
        assertEquals(listOf("Na Honjaman Level-Up", "나 혼자만 레벨업"), alternateTitles(main, alts, shown = "Solo Leveling"))
    }

    @Test
    fun alternateTitlesAreLimited() {
        val alts = (1..20).map { mapOf("en" to "Name $it") }
        assertEquals(6, alternateTitles(emptyMap(), alts, shown = "x").size)
    }

    @Test
    fun linksAreBuiltFromIdsAndAddresses() {
        val links = buildLinks(mapOf("al" to "105398", "mal" to "121496", "engtl" to "https://example.com/read", "raw" to "https://raw.example"))
        assertEquals(
            listOf(
                SeriesLink("AniList", "https://anilist.co/manga/105398"),
                SeriesLink("MyAnimeList", "https://myanimelist.net/manga/121496"),
                SeriesLink("Official English release", "https://example.com/read"),
            ),
            links,
        )
    }

    @Test
    fun missingOrBlankLinksAreDropped() {
        assertEquals(emptyList<SeriesLink>(), buildLinks(null))
        assertEquals(emptyList<SeriesLink>(), buildLinks(mapOf("al" to "", "engtl" to "not a url")))
    }

    @Test
    fun languageAndDemographicLabelsAreReadable() {
        assertEquals("Japanese", languageName("ja"))
        assertEquals("Chinese (Traditional)", languageName("zh-hk"))
        assertEquals("XX", languageName("xx"))
        assertEquals("Portuguese", languageName("pt"))
        assertEquals("Shounen", demographicLabel("shounen"))
        assertEquals(null, demographicLabel(null))
    }
}
