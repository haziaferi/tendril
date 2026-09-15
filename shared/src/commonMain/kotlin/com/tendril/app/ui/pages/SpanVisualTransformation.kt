package com.tendril.app.ui.pages

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle as ComposeSpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.tendril.app.data.page.FormattingSpan
import com.tendril.app.data.page.SpanStyle

/** §0.10 item 19 — find in page's marks, drawn through the same transformation as the spans so a
 * match inside bold text is bold and marked. Colours come from the caller (the transformation
 * is theme-blind): the marks on `accentSoft`, the current one on `accent` — one tint find
 * shares with selection until 14g's registers give it its own (`docs/critiques/find-in-page-mock.md` #1). */
class FindMarks(val ranges: List<IntRange>, val current: IntRange?, val mark: Color, val onMark: Color, val currentMark: Color, val onCurrentMark: Color)

/** Renders a block's plain-text content with its [FormattingSpan]s applied — the visual half
 * of §3.1.1's "spans over plain-text content rather than embedded markup" model. */
fun spansVisualTransformation(spans: List<FormattingSpan>, marks: FindMarks? = null): VisualTransformation = VisualTransformation { text ->
    val builder = AnnotatedString.Builder(text.text)
    spans.forEach { span ->
        val start = span.start.coerceIn(0, text.text.length)
        val end = span.end.coerceIn(start, text.text.length)
        val style = when (val s = span.style) {
            is SpanStyle.Bold -> ComposeSpanStyle(fontWeight = FontWeight.Bold)
            is SpanStyle.Italic -> ComposeSpanStyle(fontStyle = FontStyle.Italic)
            is SpanStyle.Strikethrough -> ComposeSpanStyle(textDecoration = TextDecoration.LineThrough)
            is SpanStyle.InlineCode -> ComposeSpanStyle(fontFamily = FontFamily.Monospace)
            is SpanStyle.Link -> ComposeSpanStyle(textDecoration = TextDecoration.Underline, color = Color(0xFF4A90D9))
            is SpanStyle.PageMention -> ComposeSpanStyle(fontWeight = FontWeight.SemiBold, color = Color(0xFF4A90D9))
        }
        builder.addStyle(style, start, end)
    }
    marks?.let { m ->
        val n = text.text.length
        m.ranges.forEach { r ->
            val s = r.first.coerceIn(0, n); val e = (r.last + 1).coerceIn(s, n)
            if (e > s) builder.addStyle(ComposeSpanStyle(background = m.mark, color = m.onMark), s, e)
        }
        m.current?.let { r ->
            val s = r.first.coerceIn(0, n); val e = (r.last + 1).coerceIn(s, n)
            if (e > s) builder.addStyle(ComposeSpanStyle(background = m.currentMark, color = m.onCurrentMark), s, e)
        }
    }
    TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
}
