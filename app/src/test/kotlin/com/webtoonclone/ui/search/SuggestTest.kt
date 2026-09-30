package com.webtoonclone.ui.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestTest {
    @Test
    fun suggestionsNeedTwoCharacters() {
        assertFalse(shouldSuggest(""))
        assertFalse(shouldSuggest("a"))
        assertFalse(shouldSuggest("  a "))
        assertTrue(shouldSuggest("so"))
        assertTrue(shouldSuggest(" so "))
    }
}
