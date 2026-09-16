package com.tendril.app.ui.pages

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.tendril.app.data.page.FormattingSpan
import com.tendril.app.data.page.SpanStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 14g·2 — the transformation is theme-blind: a link and a mention wear whatever accent the
 *  caller passes (B§13.8.3 rule 1), and the find marks their given colours. */
class SpanTransformationTest {

    private val link = Color(0xFF112233)
    private val mention = Color(0xFF445566)

    private fun styled(spans: List<FormattingSpan>, marks: FindMarks? = null) =
        spansVisualTransformation(spans, marks, link = link, mention = mention).filter(AnnotatedString("see reading log and Escape")).text.spanStyles

    @Test
    fun `a link is underlined in the given colour and a mention is medium in the given colour`() {
        val styles = styled(listOf(FormattingSpan(4, 15, SpanStyle.Link("https://x")), FormattingSpan(20, 26, SpanStyle.PageMention(7))))
        val linkStyle = styles.first { it.start == 4 }.item
        assertEquals(link, linkStyle.color)
        assertEquals(TextDecoration.Underline, linkStyle.textDecoration)
        val mentionStyle = styles.first { it.start == 20 }.item
        assertEquals(mention, mentionStyle.color)
        assertEquals(FontWeight.Medium, mentionStyle.fontWeight)
        assertEquals(Color.Unspecified, mentionStyle.background)
    }

    @Test
    fun `a mention carries the given background`() {
        val tint = Color(0xFFEEEEEE)
        val styles = spansVisualTransformation(listOf(FormattingSpan(20, 26, SpanStyle.PageMention(7))), null, link = link, mention = mention, mentionBackground = tint)
            .filter(AnnotatedString("see reading log and Escape")).text.spanStyles
        assertEquals(tint, styles.first { it.start == 20 }.item.background)
    }

    @Test
    fun `find marks carry their colours and the current match its own`() {
        val marks = FindMarks(
            ranges = listOf(4..10, 20..25), current = 20..25,
            mark = Color(0xFF0000AA), onMark = Color(0xFF0000BB), currentMark = Color(0xFF0000CC), onCurrentMark = Color(0xFF0000DD),
        )
        val styles = styled(emptyList(), marks)
        assertTrue(styles.any { it.start == 4 && it.item.background == Color(0xFF0000AA) && it.item.color == Color(0xFF0000BB) })
        assertTrue(styles.any { it.start == 20 && it.item.background == Color(0xFF0000CC) && it.item.color == Color(0xFF0000DD) })
    }
}
