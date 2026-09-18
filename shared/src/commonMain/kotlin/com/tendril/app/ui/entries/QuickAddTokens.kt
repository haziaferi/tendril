package com.tendril.app.ui.entries

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.tendril.app.domain.TokenSpan

/**
 * L7b (2026-09-18) — the words the parser read as a token are tinted where they sit in the line,
 * so the eye sees what was understood where it was written (Todoist's quick add, measured:
 * a tinted background behind the recognised runs, the text unchanged; the chips beneath still
 * name each token and carry its ✕). Theme-blind, like `spansVisualTransformation`: the caller
 * passes [tint] — `findSoft`, the third hue's tint that already means *recognised text* (the
 * find mark), never `accentSoft`, which is selection's (`quick-add-tokens-mock.md` #1). The
 * text keeps its own colour: `text` on `findSoft` clears 4.6:1 in every register by test.
 * Compose paints a span background as the glyph run's flat line box — no padding, no radius —
 * a stated deviation from Todoist's pill (#2). Offsets are the identity: nothing is inserted.
 */
fun quickAddTokensTransformation(spans: List<TokenSpan>, tint: Color): VisualTransformation = VisualTransformation { text ->
    val builder = AnnotatedString.Builder(text.text)
    tokenTintRanges(spans, text.text).forEach { r -> builder.addStyle(SpanStyle(background = tint), r.first, r.last + 1) }
    TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
}

/**
 * The character ranges to tint: every span, clamped to the text (the parse and the field share
 * one string, but a stale span must never index past it), trimmed of the whitespace some
 * patterns swallow (`\s*!+\s*$`, the kind prefix's `[:\s]*`) so a tint never covers a space,
 * empty ones dropped, in text order.
 */
fun tokenTintRanges(spans: List<TokenSpan>, text: String): List<IntRange> = spans
    .mapNotNull { s ->
        var a = s.start.coerceIn(0, text.length)
        var b = s.end.coerceIn(0, text.length)
        while (a < b && text[a].isWhitespace()) a++
        while (b > a && text[b - 1].isWhitespace()) b--
        if (a < b) a until b else null
    }
    .sortedBy { it.first }
