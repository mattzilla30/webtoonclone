package com.dexter.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsOrderTest {
    @Test
    fun namedRowsSortAToZAcrossSections() {
        val ids = listOf(SectionStart("B"), SettingKey("zoom"), SettingKey("Auto"), SectionStart("A"), SettingKey("middle"))
        assertEquals(listOf(2, 4, 1), settingsOrder(ids))
    }

    @Test
    fun loosePiecesStayWithTheirRow() {
        // A note after a row rides with it; a note before the first row joins that first row.
        val ids = listOf(SectionStart("S"), null, SettingKey("b"), null, SettingKey("a"))
        assertEquals(listOf(4, 1, 2, 3), settingsOrder(ids))
    }

    @Test
    fun sectionWithoutNamedRowsSortsByItsTitle() {
        val ids = listOf(SectionStart("Mid"), null, null, SectionStart("X"), SettingKey("Alpha"), SettingKey("Zulu"))
        assertEquals(listOf(4, 1, 2, 5), settingsOrder(ids))
    }
}
