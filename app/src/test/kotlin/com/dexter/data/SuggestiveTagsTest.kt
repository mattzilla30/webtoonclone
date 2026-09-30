package com.dexter.data

import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestiveTagsTest {
    @Test
    fun everySuggestiveTagIsARealMangaDexTag() {
        val known = (Genres.map { it.name } + Themes + Formats + ContentTags).toSet()
        SuggestiveTags.forEach { assertTrue("$it is not a MangaDex tag", it in known) }
    }
}
