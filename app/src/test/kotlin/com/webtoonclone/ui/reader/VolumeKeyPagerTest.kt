package com.webtoonclone.ui.reader

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VolumeKeyPagerTest {
    @Test
    fun upScrollsBackAndDownScrollsForward() {
        assertEquals(-1, volumeDirection(KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals(1, volumeDirection(KeyEvent.KEYCODE_VOLUME_DOWN))
    }

    @Test
    fun otherKeysAreIgnored() {
        assertNull(volumeDirection(KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun aPressScrollsMostOfTheScreenWithSomeOverlap() {
        assertEquals(850f, pageScrollAmount(1000, 1), 0.001f)
        assertEquals(-850f, pageScrollAmount(1000, -1), 0.001f)
    }
}
