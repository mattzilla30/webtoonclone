package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredJsonTest {
    @Test
    fun anUnknownOptionFallsBackToItsDefaultAndKeepsTheRest() {
        val raw = """{"theme":"Sepia","dataSaver":true,"blockedTags":["Gore"]}"""
        val settings = decodeStored(Settings.serializer(), raw)!!
        assertEquals(ThemeMode.Dark, settings.theme)
        assertTrue(settings.dataSaver)
        assertEquals(setOf("Gore"), settings.blockedTags)
    }

    @Test
    fun anUnknownReadingStatusReadsAsNone() {
        val raw = """{"collections":{"Faves":[{"id":"a","title":"A","status":"Someday"}]}}"""
        val library = decodeStored(LibraryData.serializer(), raw)!!
        assertNull(library.collections.getValue("Faves").single().status)
    }

    @Test
    fun brokenTextIsUnreadableAndNothingStoredIsNot() {
        assertTrue(isUnreadable(Settings.serializer(), "{not json"))
        assertFalse(isUnreadable(Settings.serializer(), null))
        assertFalse(isUnreadable(Settings.serializer(), "{}"))
    }
}
