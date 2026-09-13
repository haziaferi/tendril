package com.tendril.app.domain

import com.tendril.app.domain.ai.AI_MAX_TOKENS
import com.tendril.app.domain.ai.AiVerb
import com.tendril.app.domain.ai.describeFailure
import com.tendril.app.domain.ai.encodeRequest
import com.tendril.app.domain.ai.parseMessagesResponse
import com.tendril.app.domain.ai.request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException

/** §0.6.15 / §0.8 step 8g — what a verb sends, what comes back, and how a failure reads. */
class AiVerbsTest {

    @Test
    fun `a request carries the verb's instruction, the model and only the text`() {
        val r = request(AiVerb.SUMMARISE, "claude-sonnet-5", "pack socks and a hat")
        assertEquals("claude-sonnet-5", r.model)
        assertEquals(AI_MAX_TOKENS, r.maxTokens)
        assertEquals(AiVerb.SUMMARISE.instruction, r.system)
        assertEquals(listOf("user"), r.messages.map { it.role })
        assertEquals("pack socks and a hat", r.messages.single().content)
        val json = encodeRequest(r)
        assertTrue(json.contains("\"max_tokens\":1024"))
        assertFalse("the key is a header, never in the body", json.contains("sk-", ignoreCase = true) || json.contains("api_key") || json.contains("x-api-key"))
    }

    @Test
    fun `every verb has its own instruction`() {
        assertEquals(3, AiVerb.entries.map { it.instruction }.toSet().size)
        AiVerb.entries.forEach { assertTrue(it.instruction.contains("Return only")) }
    }

    @Test
    fun `a reply's first text block is the answer`() {
        val body = """{"id":"msg_1","type":"message","role":"assistant","content":[{"type":"text","text":"  Socks, and a hat.  "}],"model":"claude-sonnet-5","stop_reason":"end_turn"}"""
        assertEquals("Socks, and a hat.", parseMessagesResponse(body).getOrThrow())
        assertTrue(parseMessagesResponse("""{"content":[]}""").isFailure)
        assertTrue(parseMessagesResponse("not json").isFailure)
    }

    @Test
    fun `an error body reads as its message, and the status tells the rest`() {
        val err = """{"type":"error","error":{"type":"invalid_request_error","message":"max_tokens must be positive"}}"""
        assertEquals("max_tokens must be positive", parseMessagesResponse(err).exceptionOrNull()?.message)
        assertEquals("max_tokens must be positive", describeFailure(400, err, null))
        assertTrue(describeFailure(401, null, null).contains("rejected"))
        assertTrue(describeFailure(429, null, null).contains("Rate limited"))
        assertTrue(describeFailure(503, null, null).contains("unavailable"))
        assertEquals("No connection", describeFailure(null, null, ConnectException()))
    }
}
