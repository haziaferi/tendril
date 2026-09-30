package com.tendril.app.domain

import com.tendril.app.domain.plan.export.A4_HEIGHT
import com.tendril.app.domain.plan.export.A4_WIDTH
import com.tendril.app.domain.plan.export.PdfFont
import com.tendril.app.domain.plan.export.PdfWriter
import com.tendril.app.domain.plan.export.textWidth
import com.tendril.app.domain.plan.export.winAnsi
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** §6.3 (plan Phase 6, E1) — the PDF writer the exports draw with. Written before the writer. */
class PdfWriterTest {

    private fun latin1(bytes: ByteArray) = String(bytes, Charsets.ISO_8859_1)

    @Test
    fun `a document is a PDF whose cross-reference table points at every object`() {
        val pdf = PdfWriter().apply {
            page(A4_WIDTH, A4_HEIGHT) { text(40f, 60f, "One", PdfFont.BOLD, 20f) }
            page(A4_HEIGHT, A4_WIDTH) { line(40f, 40f, 200f, 40f, 0.5f, 0x999999) }
        }.toByteArray()
        val s = latin1(pdf)
        assertTrue(s.startsWith("%PDF-1.4"))
        assertTrue(s.trimEnd().endsWith("%%EOF"))
        val xref = s.lastIndexOf("\nxref\n")
        val startxref = Regex("startxref\\s+(\\d+)").find(s)!!.groupValues[1].toInt()
        assertEquals("startxref names the table", xref + 1, startxref)
        // "", "xref", "0 N", the free entry — then one row per object.
        val rows = s.substring(xref).lines().drop(4).takeWhile { it.matches(Regex("\\d{10} \\d{5} n ?")) }
        assertTrue(rows.isNotEmpty())
        rows.forEachIndexed { i, row ->
            val offset = row.substring(0, 10).toInt()
            assertTrue("object ${i + 1} is where the table says", s.startsWith("${i + 1} 0 obj", offset))
        }
        assertTrue(s.contains("/Count 2"))
        assertTrue("portrait", s.contains("/MediaBox [0 0 595.28 841.89]"))
        assertTrue("landscape", s.contains("/MediaBox [0 0 841.89 595.28]"))
    }

    @Test
    fun `text is escaped, placed from the top, and in the named font`() {
        val s = latin1(PdfWriter().apply { page(A4_WIDTH, A4_HEIGHT) { text(40f, 100f, "a (b) c\\d", PdfFont.REGULAR, 10f) } }.toByteArray())
        assertTrue(s.contains("(a \\(b\\) c\\\\d) Tj"))
        assertTrue("y measured from the top", s.contains("40 741.89 Td"))
        assertTrue(s.contains("/F1 10 Tf"))
        assertTrue(s.contains("/BaseFont /Helvetica"))
        assertTrue(s.contains("/Encoding /WinAnsiEncoding"))
    }

    @Test
    fun `characters are written in WinAnsi, and one it lacks becomes a question mark`() {
        assertArrayEquals(byteArrayOf(0x96.toByte(), 0xE8.toByte(), 0x80.toByte(), 0xB7.toByte(), '?'.code.toByte()), winAnsi("–è€·✓"))
    }

    @Test
    fun `widths are Helvetica's own`() {
        // H 722, e 556, l 222, l 222, o 556 — Adobe's AFM, per 1000 em.
        assertEquals(22.78f, textWidth("Hello", PdfFont.REGULAR, 10f), 0.001f)
        assertEquals("oblique is regular's widths", textWidth("Hello", PdfFont.REGULAR, 10f), textWidth("Hello", PdfFont.OBLIQUE, 10f), 0f)
        assertTrue("bold is wider", textWidth("Hello", PdfFont.BOLD, 10f) > textWidth("Hello", PdfFont.REGULAR, 10f))
    }
}
