package com.dexter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GenreColorTest {
    @Test
    fun matchesIgnoringCase() {
        assertNotNull(genreColor("Romance"))
        assertEquals(genreColor("romance"), genreColor("ROMANCE"))
    }

    @Test
    fun unknownOrMissingGenreHasNoColor() {
        assertNull(genreColor(null))
        assertNull(genreColor("Not A Genre"))
    }
}
