package com.webtoonclone.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class MessagesTest {
    @Test
    fun theMessageNamesTheSeriesAndTheAction() {
        assertEquals("Subscribed to Solo Leveling", subscriptionMessage("Solo Leveling", nowSubscribed = true))
        assertEquals("Unsubscribed from Solo Leveling", subscriptionMessage("Solo Leveling", nowSubscribed = false))
    }
}
