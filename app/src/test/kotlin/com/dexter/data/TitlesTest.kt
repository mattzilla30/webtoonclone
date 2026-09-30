package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TitlesTest {
    private val romaji = mapOf("ko-ro" to "Na Honjaman Level-Up")
    private val alts = listOf(mapOf("ko" to "나 혼자만 레벨업"), mapOf("en" to "Solo Leveling"), mapOf("en" to "I Level Up Alone"))

    @Test
    fun aTranslatedWorkUsesTheFirstEnglishAlternate() {
        assertEquals("Solo Leveling", chooseTitle(romaji, alts, "ko"))
    }

    @Test
    fun anEnglishOriginalKeepsItsOwnTitle() {
        assertEquals("Homestuck", chooseTitle(mapOf("en" to "Homestuck"), listOf(mapOf("en" to "Other")), "en"))
    }

    @Test
    fun aWorkWithNoEnglishAlternateKeepsItsMainTitle() {
        assertEquals("Na Honjaman Level-Up", chooseTitle(romaji, listOf(mapOf("ko" to "x")), "ko"))
    }

    @Test
    fun preferringTheOriginalAlwaysUsesTheMainTitle() {
        assertEquals("Na Honjaman Level-Up", chooseTitle(romaji, alts, "ko", preferOriginal = true))
    }
}
