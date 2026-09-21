package com.tendril.app.domain.canvas

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
    3.94f, 4.02f, 6.52f, 8.87f, 8.98f, 13.74f, 9.01f, 4.20f, 5.11f, 5.11f,  //  !"#$%&'()
    7.01f, 9.26f, 4.03f, 6.43f, 4.03f, 5.05f, 8.82f, 5.70f, 8.54f, 8.64f,  // *+,-./0123
    9.04f, 8.30f, 8.67f, 7.92f, 8.65f, 8.68f, 4.03f, 4.23f, 9.27f, 9.26f,  // 456789:;<=
    9.26f, 7.16f, 13.51f, 9.65f, 9.16f, 10.22f, 10.10f, 8.42f, 8.27f, 10.45f,  // >?@ABCDEFG
    10.40f, 3.76f, 7.99f, 9.41f, 7.92f, 12.65f, 10.55f, 10.69f, 8.94f, 10.69f,  // HIJKLMNOPQ
    9.01f, 8.98f, 9.04f, 10.42f, 9.66f, 13.80f, 9.54f, 9.50f, 8.80f, 5.10f,  // RSTUVWXYZ[
    5.05f, 5.11f, 6.60f, 6.38f, 4.52f, 7.87f, 8.57f, 8.00f, 8.57f, 8.16f,  // \]^_`abcde
    5.18f, 8.58f, 8.28f, 3.40f, 3.39f, 7.68f, 3.39f, 12.26f, 8.27f, 8.40f,  // fghijklmno
    8.57f, 8.57f, 5.27f, 7.39f, 4.59f, 8.28f, 7.87f, 11.46f, 7.64f, 7.87f,  // pqrstuvwxy
    7.73f, 5.97f, 4.65f, 5.96f, 9.26f,  // z{|}~
)

/** A lowercase letter's mean advance at 14 sp — what a character outside the table is taken as. */
const val TEXT_AVG_CHAR = 7.51f

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
    // A line's words summed here and the same line measured whole by `textWidth` differ by float
    // rounding; a card as wide as its own text must hold it on one line, so the edge is forgiven.
    val fits = innerWidth + 0.05f
    for (hard in text.split('\n')) {
        var used = 0f
        var count = 1
        for (word in hard.split(' ')) {
            val w = textWidth(word)
            if (w > fits) {
                if (used > 0f) { count++; used = 0f }
                for (c in word) {
                    val cw = charWidth(c) * TEXT_WIDTH_MARGIN
                    if (used + cw > fits && used > 0f) { count++; used = 0f }
                    used += cw
                }
                continue
            }
            val lead = if (used == 0f) 0f else space
            if (used + lead + w > fits) { count++; used = w } else used += lead + w
        }
        lines += count
    }
    return lines.coerceAtLeast(1)
}
