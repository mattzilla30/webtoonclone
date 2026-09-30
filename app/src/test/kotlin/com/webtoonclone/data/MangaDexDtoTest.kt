package com.webtoonclone.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** MangaDex sends `[]` where it means an empty map. These are the shapes a live run hit. */
class MangaDexDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun manga(attributes: String) = """{"data":[{"id":"m1","attributes":$attributes,"relationships":[]}]}"""

    private fun decode(attributes: String) = json.decodeFromString<MangaListDto>(manga(attributes)).data.single().attributes

    @Test
    fun anEmptyArrayDescriptionReadsAsNoDescription() {
        val attributes = decode("""{"title":{"en":"T"},"description":[],"status":"ongoing"}""")
        assertEquals(emptyMap<String, String>(), attributes.description)
        assertEquals("T", attributes.title["en"])
    }

    @Test
    fun anEmptyArrayLinksFieldReadsAsNoLinks() {
        val attributes = decode("""{"title":{"en":"T"},"links":[]}""")
        assertEquals(emptyMap<String, String>(), attributes.links)
    }

    @Test
    fun anEmptyArrayTitleAndAlternateTitleAreTolerated() {
        val attributes = decode("""{"title":[],"altTitles":[[],{"en":"Other"}]}""")
        assertEquals(emptyMap<String, String>(), attributes.title)
        assertEquals(listOf(emptyMap(), mapOf("en" to "Other")), attributes.altTitles)
    }

    @Test
    fun normalObjectsStillReadInFull() {
        val attributes = decode("""{"title":{"ja-ro":"Romaji"},"description":{"en":"About"},"links":{"al":"105398"},"altTitles":[{"en":"Alt"}]}""")
        assertEquals("Romaji", attributes.title["ja-ro"])
        assertEquals("About", attributes.description["en"])
        assertEquals(mapOf("al" to "105398"), attributes.links)
        assertEquals("Alt", attributes.altTitles.single()["en"])
    }

    @Test
    fun anAbsentLinksFieldStaysNull() {
        assertNull(decode("""{"title":{"en":"T"}}""").links)
    }

    @Test
    fun tagNamesAreTolerantToo() {
        val body = """{"data":[{"id":"t1","attributes":{"name":[],"group":"genre"}}]}"""
        assertEquals(emptyMap<String, String>(), json.decodeFromString<TagListDto>(body).data.single().attributes.name)
    }
}
