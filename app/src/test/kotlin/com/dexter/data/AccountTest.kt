package com.dexter.data

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountTest {
    @Test
    fun syncMergesBothWays() {
        val plan = syncPlan(local = setOf("a", "b"), remote = setOf("b", "c"))
        assertEquals(setOf("c"), plan.subscribeHere)
        assertEquals(setOf("a"), plan.followThere)
    }

    @Test
    fun ratingIsReadFromTheReply() {
        val reply = StoredJson.parseToJsonElement("""{"result":"ok","ratings":{"s1":{"rating":8,"createdAt":"x"}}}""").jsonObject
        assertEquals(8, ratingFrom(reply, "s1"))
        assertNull(ratingFrom(reply, "s2"))
    }

    @Test
    fun tokenReplyParses() {
        val token = StoredJson.decodeFromString(TokenDto.serializer(), """{"access_token":"a","refresh_token":"r","expires_in":900,"token_type":"Bearer"}""")
        assertEquals("a", token.accessToken)
        assertEquals("r", token.refreshToken)
    }
}
