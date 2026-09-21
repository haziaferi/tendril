# -*- coding: utf-8 -*-
"""Regenerates domain/canvas/TextWidth.kt from the bundled Inter (S14): the 95 ASCII advances at 14 sp, opsz 14 / wght 400, unhinted. Run when the typeface changes."""
from PIL import ImageFont
import statistics
import os
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..") + "/"
font = ImageFont.truetype(ROOT + "shared/src/commonMain/composeResources/font/inter_variable.ttf", 1400); font.set_variation_by_axes([14, 400])
adv = [font.getlength(chr(c)) / 100 for c in range(32, 127)]
lower = statistics.mean(font.getlength(chr(c)) / 100 for c in range(97, 123))
rows = []
for i in range(0, 95, 10):
    chars = "".join(chr(32 + j) for j in range(i, min(i + 10, 95))).replace("*/", "* /")
    rows.append("    " + ", ".join("%.2ff" % a for a in adv[i:i + 10]) + ",  // " + chars)
body = "\n".join(rows)
kt = '''package com.tendril.app.domain.canvas

/**
 * S14 (2026-09-21) — a canvas text's width without a text measurer: the layout (`tidy`), the hit-tests,
 * the arrows' anchors, the embed's fit and the JSON Canvas export run in the domain, so a card's and a
 * leaf's box come from an estimate. It had been a flat dp per character (7.5 for a leaf, 8.5 for a
 * card), and a flat rate cannot be right for both *Weeds* (9.2 dp a character) and *Lawn clippings*
 * (6.6): the leaf cut *Shredded paper* (item 24's S14) while short words sat in air. These are the
 * bundled Inter Regular's advances at 14 sp (`body`) for printable ASCII, index = code − 32, read
 * from `inter_variable.ttf` unhinted to 0.01 dp; DM Sans is within one per cent of them and Source
 * Serif narrower, so Inter bounds the three typefaces. A character outside the table takes a
 * lowercase letter's mean; [TEXT_WIDTH_MARGIN] covers the typefaces' spread and Compose's rounding.
 */
private val INTER_14_ADVANCES: FloatArray = floatArrayOf(
@BODY@
)

/** A lowercase letter's mean advance at 14 sp — what a character outside the table is taken as. */
const val TEXT_AVG_CHAR = @AVG@f

/** The margin over the summed advances. */
const val TEXT_WIDTH_MARGIN = 1.05f

/** The advance of one character at 14 sp. */
fun charWidth(c: Char): Float {
    val i = c.code - 32
    return if (i in INTER_14_ADVANCES.indices) INTER_14_ADVANCES[i] else TEXT_AVG_CHAR
}

/** The width one line of [text] takes at `body` (14 sp), with the margin; an empty text is 0. */
fun textWidth(text: String): Float {
    var w = 0f
    for (c in text) w += charWidth(c)
    return w * TEXT_WIDTH_MARGIN
}

/**
 * The lines [text] wraps to inside [innerWidth] at `body`: hard breaks count, words wrap greedily
 * on spaces, a word wider than a line starts its own and breaks by characters (what `Text` does
 * with `softWrap`).
 */
fun wrappedLines(text: String, innerWidth: Float): Int {
    var lines = 0
    val space = charWidth(' ') * TEXT_WIDTH_MARGIN
    for (hard in text.split('\\n')) {
        var used = 0f
        var count = 1
        for (word in hard.split(' ')) {
            val w = textWidth(word)
            if (w > innerWidth) {
                if (used > 0f) { count++; used = 0f }
                for (c in word) {
                    val cw = charWidth(c) * TEXT_WIDTH_MARGIN
                    if (used + cw > innerWidth && used > 0f) { count++; used = 0f }
                    used += cw
                }
                continue
            }
            val lead = if (used == 0f) 0f else space
            if (used + lead + w > innerWidth) { count++; used = w } else used += lead + w
        }
        lines += count
    }
    return lines.coerceAtLeast(1)
}
'''.replace("@BODY@", body).replace("@AVG@", "%.2f" % lower)
open(ROOT + "shared/src/commonMain/kotlin/com/tendril/app/domain/canvas/TextWidth.kt", "w", encoding="utf-8", newline="").write(kt)

# the numbers the tests and the critique cite
def tw(s): return sum(adv[ord(c) - 32] if 32 <= ord(c) < 127 else lower for c in s) * 1.05
for w in ("Shredded paper", "Weeds", "Compost", "Kitchen scraps", "Empty card", "Water", "Lawn clippings", "Turn the compost every second week in spring", "Beds"):
    print("%-46s %.2f" % (w, tw(w)))
