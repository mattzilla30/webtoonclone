package com.dexter.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LicensesTest {
    @Test
    fun componentsAreAlphabetical() {
        val names = OpenSourceComponents.map { it.name }
        assertEquals(names.sortedWith(String.CASE_INSENSITIVE_ORDER), names)
    }

    @Test
    fun everyComponentLinksSomewhere() {
        assertTrue(OpenSourceComponents.all { it.url.startsWith("http") && it.license.isNotBlank() })
    }

    /** The repository carries the full GPL-3.0 text the licenses page points to. */
    @Test
    fun repositoryHasTheGplText() {
        val license = File("../LICENSE").readText()
        assertTrue(license.contains("GNU GENERAL PUBLIC LICENSE") && license.contains("Version 3, 29 June 2007"))
    }
}
