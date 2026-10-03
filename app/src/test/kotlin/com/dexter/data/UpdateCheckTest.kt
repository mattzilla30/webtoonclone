package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    @Test
    fun comparesNumberByNumber() {
        assertTrue(isNewer("v0.2.0", "0.1.0"))
        assertTrue(isNewer("0.1.10", "0.1.9"))
        assertFalse(isNewer("v0.1.0", "0.1.0"))
        assertTrue(isNewer("1.0.0-beta.10", "1.0.0-beta.2"))
        assertTrue(isNewer("1.0.0", "1.0.0-rc.1"))
        assertFalse(isNewer("1.0.0-beta", "1.0.0"))
        assertFalse(isNewer("0.0.9", "0.1.0"))
        assertTrue(isNewer("1.0", "0.9.9"))
    }

    @Test
    fun releaseFindsItsApk() {
        val release = StoredJson.decodeFromString(
            Release.serializer(),
            """{"tag_name":"v0.2.0","html_url":"https://github.com/x","assets":[{"name":"notes.txt","browser_download_url":"a"},{"name":"dexter.apk","browser_download_url":"b"}]}""",
        )
        assertEquals("b", release.apk)
    }
}
