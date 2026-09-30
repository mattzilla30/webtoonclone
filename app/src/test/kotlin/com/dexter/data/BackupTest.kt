package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupTest {
    @Test
    fun roundTripKeepsEverything() {
        val backup = Backup(
            savedAt = 5L,
            library = LibraryData(
                recent = listOf(SavedSeries("a", "A", null, "c1", "3")),
                lists = listOf(SavedSeries("b", "B", null, status = ReadingStatus.Dropped)),
                searches = listOf("one piece"),
            ),
            settings = Settings(theme = ThemeMode.Black),
            progress = mapOf("a" to "c1:4"),
        )
        assertEquals(backup, decodeBackup(encodeBackup(backup)))
    }

    @Test
    fun rejectsGarbageAndNewerVersions() {
        assertNull(decodeBackup("not json"))
        assertNull(decodeBackup("""{"version":99}"""))
    }

    @Test
    fun ignoresUnknownFields() {
        assertEquals(1, decodeBackup("""{"version":1,"future":true}""")?.version)
    }
}
