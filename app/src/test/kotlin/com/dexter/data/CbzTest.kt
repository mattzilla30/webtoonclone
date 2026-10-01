package com.dexter.data

import com.dexter.data.db.DownloadEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CbzTest {
    private val row = DownloadEntity("c1", "s1", "Tom & Jerry: <Redux>", null, "12", "A/B", "3", "Group", "", 20, 0, 0)

    @Test
    fun nameDropsCharactersFileSystemsReject() {
        assertEquals("Tom & Jerry Redux Ep 12.cbz", cbzName(row))
    }

    @Test
    fun comicInfoEscapesText() {
        val info = comicInfoXml(row)
        assertTrue(info.contains("<Series>Tom &amp; Jerry: &lt;Redux&gt;</Series>"))
        assertTrue(info.contains("<Number>12</Number>"))
        assertTrue(info.contains("<PageCount>20</PageCount>"))
    }
}
