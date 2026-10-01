package com.dexter.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class LenientStringMapTest {
    private val json = Json

    private fun read(text: String) = json.decodeFromString(LenientStringMap, text)

    @Test
    fun readsAnObjectOfText() {
        assertEquals(mapOf("en" to "Title", "ja" to "題"), read("""{"en":"Title","ja":"題"}"""))
    }

    @Test
    fun emptyArrayReadsAsEmptyMap() {
        assertEquals(emptyMap<String, String>(), read("[]"))
    }

    @Test
    fun skipsValuesThatAreNotText() {
        assertEquals(mapOf("en" to "Title"), read("""{"en":"Title","x":{"a":1},"y":[1]}"""))
    }

    @Test
    fun otherShapesReadAsEmptyMap() {
        assertEquals(emptyMap<String, String>(), read("\"text\""))
        assertEquals(emptyMap<String, String>(), read("5"))
    }
}
