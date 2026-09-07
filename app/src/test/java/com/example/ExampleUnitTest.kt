package com.example

import com.example.bot.CustomReplyEngine
import com.example.model.CustomReply
import com.example.model.MatchMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testCustomReplyEngine_CaseInsensitiveMatch() {
        val engine = CustomReplyEngine()
        val rules = listOf(
            CustomReply(id = 1, trigger = "hi", reply = "Hello! How can I help you?", matchMode = MatchMode.CASE_INSENSITIVE.name, isEnabled = true)
        )

        val matchLower = engine.findMatchingReply("hi", rules)
        assertNotNull(matchLower)
        assertEquals("Hello! How can I help you?", matchLower?.reply)

        val matchUpper = engine.findMatchingReply("HI", rules)
        assertNotNull(matchUpper)

        val matchPunctuation = engine.findMatchingReply("Hi!", rules)
        assertNotNull(matchPunctuation)

        val matchSpaces = engine.findMatchingReply("   hi   ", rules)
        assertNotNull(matchSpaces)
    }

    @Test
    fun testCustomReplyEngine_ExactMatch() {
        val engine = CustomReplyEngine()
        val rules = listOf(
            CustomReply(id = 2, trigger = "VIP_ACCESS", reply = "Welcome VIP!", matchMode = MatchMode.EXACT.name, isEnabled = true)
        )

        val matchExact = engine.findMatchingReply("VIP_ACCESS", rules)
        assertNotNull(matchExact)

        val matchWrongCase = engine.findMatchingReply("vip_access", rules)
        assertNull(matchWrongCase)
    }

    @Test
    fun testCustomReplyEngine_ContainsMatch() {
        val engine = CustomReplyEngine()
        val rules = listOf(
            CustomReply(id = 3, trigger = "pricing", reply = "Check our website for pricing!", matchMode = MatchMode.CONTAINS.name, isEnabled = true)
        )

        val matchContained = engine.findMatchingReply("Can you tell me about pricing plans?", rules)
        assertNotNull(matchContained)
        assertEquals("Check our website for pricing!", matchContained?.reply)
    }

    @Test
    fun testCustomReplyEngine_DisabledRuleIgnored() {
        val engine = CustomReplyEngine()
        val rules = listOf(
            CustomReply(id = 4, trigger = "hello", reply = "Hi", matchMode = MatchMode.CASE_INSENSITIVE.name, isEnabled = false)
        )

        val match = engine.findMatchingReply("hello", rules)
        assertNull(match)
    }
}
