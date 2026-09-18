package com.tendril.app.ui.entries

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.domain.QuickAddParser
import com.tendril.app.domain.TokenKind
import com.tendril.app.domain.TokenSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** L7b (2026-09-18) — the parser's spans become flat tinted runs in the line; nothing else moves. */
class QuickAddTokensTest {

    private val tint = Color(0xFF5D564A)
    private val today = LocalDate.of(2026, 9, 18)

    @Test
    fun `every recognised run of a line is tinted, the title's words are not`() {
        val line = "Dentist fri 14:30 !"
        val parsed = QuickAddParser.parse(line, today, EntryKind.TASK)
        val out = quickAddTokensTransformation(parsed.spans, tint).filter(AnnotatedString(line))
        assertEquals(line, out.text.text)
        assertSame(OffsetMapping.Identity, out.offsetMapping)
        val tinted = out.text.spanStyles.map { line.substring(it.start, it.end) to it.item.background }
        assertEquals(setOf("fri", "14:30", "!"), tinted.map { it.first }.toSet())
        assertTrue(tinted.all { it.second == tint })
    }

    @Test
    fun `a dropped token loses its tint with its chip`() {
        val line = "Dentist fri 14:30 !"
        val parsed = QuickAddParser.parse(line, today, EntryKind.TASK, ignore = setOf(TokenKind.TIME))
        val runs = tokenTintRanges(parsed.spans, line).map { line.substring(it.first, it.last + 1) }
        assertEquals(listOf("fri", "!"), runs)
    }

    @Test
    fun `ranges are clamped to the text, trimmed of whitespace, empty ones dropped, in text order`() {
        val text = "ab cd  ef gh"
        val spans = listOf(TokenSpan(9, 14, TokenKind.DATE), TokenSpan(2, 5, TokenKind.TIME), TokenSpan(5, 7, TokenKind.SPAN), TokenSpan(-3, 1, TokenKind.KIND))
        assertEquals(listOf(0..0, 3..4, 10..11), tokenTintRanges(spans, text))
    }
}
