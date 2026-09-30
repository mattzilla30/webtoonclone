package com.webtoonclone.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinksTest {
    private val id = "a1c7c817-4e59-43b7-9365-09675a149a6f"

    @Test
    fun titleLinkWithSlug() {
        assertEquals(MangaDexLink.Title(id), parseMangaDexLink("mangadex.org", listOf("title", id, "one-piece")))
    }

    @Test
    fun chapterLinkWithPage() {
        assertEquals(MangaDexLink.Chapter(id), parseMangaDexLink("www.mangadex.org", listOf("chapter", id, "3")))
    }

    @Test
    fun rejectsOtherHostsKindsAndIds() {
        assertNull(parseMangaDexLink("example.com", listOf("title", id)))
        assertNull(parseMangaDexLink("mangadex.org", listOf("group", id)))
        assertNull(parseMangaDexLink("mangadex.org", listOf("title", "nope")))
        assertNull(parseMangaDexLink("mangadex.org", emptyList()))
        assertNull(parseMangaDexLink(null, listOf("title", id)))
    }
}
