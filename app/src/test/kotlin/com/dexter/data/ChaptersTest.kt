package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChaptersTest {
    private fun chapter(id: String, group: String? = null, external: Boolean = false, volume: String? = null) =
        Chapter(id, "1", "", "", if (external) "https://example.com" else null, group, volume)

    @Test
    fun thePreferredGroupWinsWhenItUploadedTheChapter() {
        val picked = pickUpload(listOf(chapter("a", "Alpha"), chapter("b", "Beta")), preferredGroup = "Beta")
        assertEquals("b", picked.id)
        assertEquals(listOf("a"), picked.alternates.map { it.id })
    }

    @Test
    fun withoutAPreferenceTheFirstReadableUploadWins() {
        val picked = pickUpload(listOf(chapter("a", "Alpha"), chapter("b", "Beta")), preferredGroup = null)
        assertEquals("a", picked.id)
    }

    @Test
    fun aReadableUploadBeatsALinkOut() {
        val picked = pickUpload(listOf(chapter("ext", "Publisher", external = true), chapter("read", "Scans")), preferredGroup = null)
        assertEquals("read", picked.id)
    }

    @Test
    fun aPreferredGroupThatOnlyLinksOutDoesNotBeatAReadableUpload() {
        val picked = pickUpload(listOf(chapter("ext", "Publisher", external = true), chapter("read", "Scans")), preferredGroup = "Publisher")
        assertEquals("read", picked.id)
    }

    @Test
    fun aSingleUploadHasNoAlternates() {
        val picked = pickUpload(listOf(chapter("only")), preferredGroup = "Anyone")
        assertEquals("only", picked.id)
        assertTrue(picked.alternates.isEmpty())
    }

    @Test
    fun volumeHeadingsAppearWhereTheVolumeChanges() {
        val list = listOf(chapter("c4", volume = "2"), chapter("c3", volume = "2"), chapter("c2", volume = "1"), chapter("c1"))
        val items = groupByVolume(list)
        assertEquals(
            listOf("Volume 2", "c4", "c3", "Volume 1", "c2", "No volume", "c1"),
            items.map { if (it is ChapterListItem.VolumeHeader) it.label else (it as ChapterListItem.Entry).chapter.id },
        )
    }

    @Test
    fun aListWithNoVolumesGetsNoHeadings() {
        val items = groupByVolume(listOf(chapter("c2"), chapter("c1")))
        assertTrue(items.all { it is ChapterListItem.Entry })
    }
}
