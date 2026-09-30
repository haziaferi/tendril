package com.tendril.app.domain.plan.export

import java.io.ByteArrayOutputStream
import java.util.Locale

/*
 * §6.3 (plan Phase 6, E1) — the smallest PDF writer the plan's A4 sheets need: pages of text, lines
 * and rectangles in PDF's three built-in Helvetica faces, WinAnsi-encoded. One writer for both apps,
 * so the phone and the desktop produce the same file and the unit suite can read it back; no
 * dependency, because none is in the offline cache and a text sheet does not warrant one.
 *
 * Coordinates are points from the page's top-left corner, as a layout thinks of them; `y` is a
 * text's baseline. Streams are left uncompressed: a sheet is a few kilobytes, and a readable stream
 * is what lets a test assert what was drawn.
 */

const val A4_WIDTH = 595.28f
const val A4_HEIGHT = 841.89f

/** The built-in faces used: no font is embedded, every PDF reader carries these three. */
enum class PdfFont(internal val resource: String, internal val baseFont: String) {
    REGULAR("F1", "Helvetica"),
    BOLD("F2", "Helvetica-Bold"),
    OBLIQUE("F3", "Helvetica-Oblique"),
}

class PdfWriter {
    private val pages = mutableListOf<PdfCanvas>()

    /** Adds a page [width] × [height] points and draws it now. */
    fun page(width: Float, height: Float, draw: PdfCanvas.() -> Unit) {
        pages += PdfCanvas(width, height).apply(draw)
    }

    val pageCount: Int get() = pages.size

    fun toByteArray(): ByteArray {
        val out = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()
        fun ascii(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
        fun obj(body: () -> Unit) {
            offsets += out.size()
            ascii("${offsets.size} 0 obj\n"); body(); ascii("\nendobj\n")
        }
        ascii("%PDF-1.4\n%âãÏÓ\n")
        val fontBase = 3
        val pageBase = fontBase + PdfFont.entries.size
        val kids = pages.indices.joinToString(" ") { "${pageBase + it * 2} 0 R" }
        obj { ascii("<< /Type /Catalog /Pages 2 0 R >>") }
        obj { ascii("<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>") }
        for (f in PdfFont.entries) obj { ascii("<< /Type /Font /Subtype /Type1 /BaseFont /${f.baseFont} /Encoding /WinAnsiEncoding >>") }
        val fonts = PdfFont.entries.mapIndexed { i, f -> "/${f.resource} ${fontBase + i} 0 R" }.joinToString(" ")
        pages.forEachIndexed { i, p ->
            val content = p.content()
            obj {
                ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${num(p.width)} ${num(p.height)}] ")
                ascii("/Resources << /Font << $fonts >> >> /Contents ${pageBase + i * 2 + 1} 0 R >>")
            }
            obj { ascii("<< /Length ${content.size} >>\nstream\n"); out.write(content); ascii("\nendstream") }
        }
        val xref = out.size()
        ascii("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        for (o in offsets) ascii("%010d 00000 n \n".format(o))
        ascii("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }
}

class PdfCanvas internal constructor(val width: Float, val height: Float) {
    private val ops = ByteArrayOutputStream()
    private fun ascii(s: String) = ops.write(s.toByteArray(Charsets.ISO_8859_1))

    /** [text] with its baseline [y] points from the top, starting [x] from the left. */
    fun text(x: Float, y: Float, text: String, font: PdfFont, size: Float, color: Int = 0x000000) {
        if (text.isEmpty()) return
        ascii("BT /${font.resource} ${num(size)} Tf ${rgb(color)} rg ${num(x)} ${num(height - y)} Td (")
        for (b in winAnsi(text)) {
            val c = b.toInt() and 0xFF
            if (c == '('.code || c == ')'.code || c == '\\'.code) ops.write('\\'.code)
            ops.write(c)
        }
        ascii(") Tj ET\n")
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Int) {
        ascii("${num(width)} w ${rgb(color)} RG ${num(x1)} ${num(height - y1)} m ${num(x2)} ${num(height - y2)} l S\n")
    }

    /** A rectangle whose top-left corner is ([x], [y]); filled, stroked, or both. */
    fun rect(x: Float, y: Float, w: Float, h: Float, fill: Int?, stroke: Int? = null, strokeWidth: Float = 0.5f) {
        if (fill == null && stroke == null) return
        val box = "${num(x)} ${num(height - y - h)} ${num(w)} ${num(h)} re"
        if (fill != null) ascii("${rgb(fill)} rg ")
        if (stroke != null) ascii("${num(strokeWidth)} w ${rgb(stroke)} RG ")
        ascii("$box ${if (fill != null && stroke != null) "B" else if (fill != null) "f" else "S"}\n")
    }

    internal fun content(): ByteArray = ops.toByteArray()
}

/** Up to two decimals, no trailing zeros, never a locale's comma. */
private fun num(v: Float): String {
    val s = String.format(Locale.ROOT, "%.2f", v)
    return s.trimEnd('0').trimEnd('.').let { if (it == "-0") "0" else it }
}

private fun rgb(c: Int): String = listOf(c shr 16, c shr 8, c).joinToString(" ") { num((it and 0xFF) / 255f) }

/** Windows-1252's own characters in 0x80–0x9F; the rest of its upper half is Latin-1's. */
private val CP1252_HIGH = mapOf(
    '€' to 0x80, '‚' to 0x82, 'ƒ' to 0x83, '„' to 0x84, '…' to 0x85, '†' to 0x86,
    '‡' to 0x87, 'ˆ' to 0x88, '‰' to 0x89, 'Š' to 0x8A, '‹' to 0x8B, 'Œ' to 0x8C,
    'Ž' to 0x8E, '‘' to 0x91, '’' to 0x92, '“' to 0x93, '”' to 0x94, '•' to 0x95,
    '–' to 0x96, '—' to 0x97, '˜' to 0x98, '™' to 0x99, 'š' to 0x9A, '›' to 0x9B,
    'œ' to 0x9C, 'ž' to 0x9E, 'Ÿ' to 0x9F,
)

/** [text] as WinAnsi bytes: what the built-in faces can draw, and `?` for anything else (one per character, surrogate pairs included). */
fun winAnsi(text: String): ByteArray {
    val out = ByteArrayOutputStream()
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        val code = when {
            ch.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate() -> { i++; '?'.code }
            ch == '\t' || ch == '\n' || ch == '\r' -> ' '.code
            ch.code in 0x20..0x7E || ch.code in 0xA0..0xFF -> ch.code
            else -> CP1252_HIGH[ch] ?: '?'.code
        }
        out.write(code)
        i++
    }
    return out.toByteArray()
}

/** [text]'s advance at [size] points, from Helvetica's own metrics (the oblique face has the regular widths). */
fun textWidth(text: String, font: PdfFont, size: Float): Float {
    val table = if (font == PdfFont.BOLD) HELVETICA_BOLD else HELVETICA
    var units = 0
    for (b in winAnsi(text)) {
        val c = b.toInt() and 0xFF
        if (c >= 32) units += table[c - 32]
    }
    return units * size / 1000f
}

/**
 * Advance widths for WinAnsi codes 32–255, in 1/1000 em, read from Adobe's Core 14 AFM files
 * (Helvetica.afm, Helvetica-Bold.afm; each glyph found by its Adobe Glyph List name). Zero marks
 * the five codes Windows-1252 leaves undefined, and DEL.
 *
 * "Copyright (c) 1985, 1987, 1989, 1990, 1997 Adobe Systems Incorporated. All Rights Reserved."
 * The AFM files "may be used, copied, and distributed for any purpose and without charge, with or
 * without modification, provided that all copyright notices are retained". Modified here: only the
 * widths are kept, re-ordered by WinAnsi code.
 */
private val HELVETICA = intArrayOf(
    278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
    556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
    1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
    667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
    333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
    556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584, 0,
    556, 0, 222, 556, 333, 1000, 556, 556, 333, 1000, 667, 333, 1000, 0, 611, 0,
    0, 222, 222, 333, 333, 350, 556, 1000, 333, 1000, 500, 333, 944, 0, 500, 667,
    278, 333, 556, 556, 556, 556, 260, 556, 333, 737, 370, 556, 584, 333, 737, 333,
    400, 584, 333, 333, 333, 556, 537, 278, 333, 333, 365, 556, 834, 834, 834, 611,
    667, 667, 667, 667, 667, 667, 1000, 722, 667, 667, 667, 667, 278, 278, 278, 278,
    722, 722, 778, 778, 778, 778, 778, 584, 778, 722, 722, 722, 722, 667, 667, 611,
    556, 556, 556, 556, 556, 556, 889, 500, 556, 556, 556, 556, 278, 278, 278, 278,
    556, 556, 556, 556, 556, 556, 556, 584, 611, 556, 556, 556, 556, 500, 556, 500,
)

private val HELVETICA_BOLD = intArrayOf(
    278, 333, 474, 556, 556, 889, 722, 238, 333, 333, 389, 584, 278, 333, 278, 278,
    556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 333, 333, 584, 584, 584, 611,
    975, 722, 722, 722, 722, 667, 611, 778, 722, 278, 556, 722, 611, 833, 722, 778,
    667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 333, 278, 333, 584, 556,
    333, 556, 611, 556, 611, 556, 333, 611, 611, 278, 278, 556, 278, 889, 611, 611,
    611, 611, 389, 556, 333, 611, 556, 778, 556, 556, 500, 389, 280, 389, 584, 0,
    556, 0, 278, 556, 500, 1000, 556, 556, 333, 1000, 667, 333, 1000, 0, 611, 0,
    0, 278, 278, 500, 500, 350, 556, 1000, 333, 1000, 556, 333, 944, 0, 500, 667,
    278, 333, 556, 556, 556, 556, 280, 556, 333, 737, 370, 556, 584, 333, 737, 333,
    400, 584, 333, 333, 333, 611, 556, 278, 333, 333, 365, 556, 834, 834, 834, 611,
    722, 722, 722, 722, 722, 722, 1000, 722, 667, 667, 667, 667, 278, 278, 278, 278,
    722, 722, 778, 778, 778, 778, 778, 584, 778, 722, 722, 722, 722, 667, 667, 611,
    556, 556, 556, 556, 556, 556, 889, 556, 556, 556, 556, 556, 278, 278, 278, 278,
    611, 611, 611, 611, 611, 611, 611, 584, 611, 611, 611, 611, 611, 556, 611, 556,
)
