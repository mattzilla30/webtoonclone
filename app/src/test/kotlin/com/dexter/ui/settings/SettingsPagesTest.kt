package com.dexter.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class SettingsPagesTest {
    @Test
    fun pagesAreAlphabetical() {
        assertEquals(SettingsPages.sortedWith(String.CASE_INSENSITIVE_ORDER), SettingsPages)
    }

    /** Every block in the settings code sits on one page, and every listed section has a block, so nothing is unreachable. */
    @Test
    fun everyBlockHasAPage() {
        val block = Regex("""SettingsBlock\("([^"]+)"\)""")
        val titles = File("src/main/kotlin/com/dexter/ui/settings").listFiles()!!
            .filter { it.extension == "kt" }
            .flatMap { file -> block.findAll(file.readText()).map { it.groupValues[1] }.toList() }
        val grouped = SettingsPageGroups.values.flatten()
        assertEquals("every section on exactly one page", grouped.size, grouped.toSet().size)
        assertEquals(grouped.toSet(), titles.toSet())
        assertEquals("each title once", titles.size, titles.toSet().size)
    }
}
