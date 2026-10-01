package com.dexter.notify

import com.dexter.data.HttpStatusException
import com.dexter.data.SavedSeries
import com.dexter.data.isWorthRetrying
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class FeedCheckTest {
    private val tracked = SavedSeries("s", "Series", null, knownChapterId = "c1", knownChapterNumber = "10")

    @Test
    fun noNewUploadSkipsTheFeed() {
        assertFalse(needsFeedCheck(tracked, lastUpload = "u1", newestUpload = "u1"))
    }

    @Test
    fun aNewUploadReadsTheFeed() {
        assertTrue(needsFeedCheck(tracked, lastUpload = "u1", newestUpload = "u2"))
    }

    @Test
    fun anUnknownUploadOrFirstLookReadsTheFeed() {
        assertTrue(needsFeedCheck(tracked, lastUpload = "u1", newestUpload = null))
        assertTrue(needsFeedCheck(tracked, lastUpload = null, newestUpload = "u1"))
        assertTrue(needsFeedCheck(tracked.copy(knownChapterId = null), lastUpload = "u1", newestUpload = "u1"))
    }

    @Test
    fun onlyPassingFailuresAreWorthRetrying() {
        assertTrue(isWorthRetrying(IOException("connection reset")))
        assertTrue(isWorthRetrying(HttpStatusException(429, "rate limited")))
        assertTrue(isWorthRetrying(HttpStatusException(503, "down")))
        assertFalse(isWorthRetrying(HttpStatusException(404, "gone")))
        assertFalse(isWorthRetrying(SerializationException("bad reply")))
    }
}
