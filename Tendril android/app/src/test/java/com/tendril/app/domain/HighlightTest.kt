package com.tendril.app.domain

import com.tendril.app.domain.code.CodeToken
import com.tendril.app.domain.code.TokenKind
import com.tendril.app.domain.code.highlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** §3.1.1's code block highlighted (2026-09-22) — the tokenizer over the named language set and the generic pass. */
class HighlightTest {

    private fun kinds(code: String, language: String?) = highlight(code, language).map { it.kind to code.substring(it.start, it.end) }

    @Test
    fun `kotlin - keywords, a string with an escape, a line and a block comment, a number`() {
        val code = "val n = 42 // count\nfun f(s: String) = \"a \\\"b\\\"\" /* x */ + n1"
        val got = kinds(code, "kotlin")
        assertEquals(TokenKind.KEYWORD to "val", got[0])
        assertEquals(TokenKind.NUMBER to "42", got[1])
        assertEquals(TokenKind.COMMENT to "// count", got[2])
        assertEquals(TokenKind.KEYWORD to "fun", got[3])
        assertEquals(TokenKind.STRING to "\"a \\\"b\\\"\"", got[4])
        assertEquals(TokenKind.COMMENT to "/* x */", got[5])
        assertEquals(6, got.size)                                   // `n1` is an identifier, not a number; `String` and `f` are not keywords
    }

    @Test
    fun `python - hash comments and a triple-quoted string spanning lines`() {
        val code = "def f():\n    \"\"\"doc\n    string\"\"\"  # note\n    return None"
        val got = kinds(code, "py")
        assertEquals(listOf(TokenKind.KEYWORD to "def", TokenKind.STRING to "\"\"\"doc\n    string\"\"\"", TokenKind.COMMENT to "# note",
            TokenKind.KEYWORD to "return", TokenKind.KEYWORD to "None"), got)
    }

    @Test
    fun `sql is case-insensitive, json knows its three literals, markdown its headings and code spans`() {
        assertEquals(listOf(TokenKind.KEYWORD to "SELECT", TokenKind.KEYWORD to "from", TokenKind.KEYWORD to "where", TokenKind.STRING to "'x'", TokenKind.COMMENT to "-- c"),
            kinds("SELECT a from t where b = 'x' -- c", "sql"))
        assertEquals(listOf(TokenKind.STRING to "\"k\"", TokenKind.KEYWORD to "true", TokenKind.STRING to "\"n\"", TokenKind.NUMBER to "3"), kinds("{\"k\": true, \"n\": 3}", "json"))
        assertEquals(listOf(TokenKind.KEYWORD to "# Title", TokenKind.STRING to "`code`"), kinds("# Title\nsome `code` here", "markdown"))
    }

    @Test
    fun `an unknown tag gets strings and numbers only, and a glued apostrophe is prose`() {
        assertEquals(listOf(TokenKind.STRING to "\"hi\"", TokenKind.NUMBER to "7"), kinds("say \"hi\" 7 times if you don't mind", null))
        assertEquals(listOf(TokenKind.COMMENT to "// c"), kinds("int x; // c", "java"))
    }

    @Test
    fun `tokens never overlap, are in order, and an unterminated string or comment runs to the end`() {
        val code = "/* open\nfor ever \"and"
        val t = highlight(code, "kotlin")
        assertEquals(listOf(CodeToken(0, code.length, TokenKind.COMMENT)), t)
        val s = highlight("x = 'abc", "python")
        assertEquals(listOf(CodeToken(4, 8, TokenKind.STRING)), s)
        val many = highlight("val a = \"s\" // c\nval b = 1", "kotlin")
        assertTrue(many.zipWithNext().all { (p, q) -> p.end <= q.start })
    }
}
