package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguagesTest {
    @Test
    fun englishIsFirstAndCodesAreUnique() {
        assertEquals("en", Languages.first().code)
        assertEquals(Languages.size, Languages.map { it.code }.toSet().size)
    }

    @Test
    fun theSettingDefaultsToEnglishAndIsListed() {
        assertTrue(Languages.any { it.code == Settings().language })
    }
}
