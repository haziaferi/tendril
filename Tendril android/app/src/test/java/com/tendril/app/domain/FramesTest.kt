package com.tendril.app.domain

import com.tendril.app.data.canvas.CanvasNode
import com.tendril.app.data.canvas.CanvasNodeType
import com.tendril.app.domain.canvas.CANVAS_NODE_H
import com.tendril.app.domain.canvas.CANVAS_CARD_MIN_W
import com.tendril.app.domain.canvas.CANVAS_CARD_WRAP_W
import com.tendril.app.domain.canvas.CANVAS_LEGACY_CARD_W
import com.tendril.app.domain.canvas.cardWidth
import com.tendril.app.domain.canvas.cardChars
import com.tendril.app.domain.canvas.handWidth
import com.tendril.app.domain.canvas.FRAME_MIN_H
import com.tendril.app.domain.canvas.FRAME_MIN_W
import com.tendril.app.domain.canvas.NodeBox
import com.tendril.app.domain.canvas.clampFrameSize
import com.tendril.app.domain.canvas.embedHeightForBoxes
import com.tendril.app.domain.canvas.fitToBoxes
import com.tendril.app.domain.canvas.cardHeight
import com.tendril.app.domain.canvas.cardLines
import com.tendril.app.domain.canvas.nodeBox
import com.tendril.app.domain.canvas.nodesInside
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** §0.10 item 15 — frames: geometry containment, the size floor, and the fit over boxes of two kinds. */
class FramesTest {
    private val t0 = Instant.EPOCH
    private var nextId = 1L
    private fun card(x: Float, y: Float) = CanvasNode(id = nextId++, canvasId = 1, type = CanvasNodeType.TEXT, x = x, y = y, text = "", createdAt = t0, updatedAt = t0)
    private fun frame(x: Float, y: Float, w: Float, h: Float) = CanvasNode(id = nextId++, canvasId = 1, type = CanvasNodeType.FRAME, x = x, y = y, width = w, height = h, text = "F", createdAt = t0, updatedAt = t0)

    @Test
    fun `a node is inside a frame only when its whole box is`() {
        val f = frame(0f, 0f, 400f, 300f)
        val inside = card(10f, 10f)                       // an empty card is 101 wide (its caption): 10…111 × 10…58
        val onTheEdge = card(299f, 252f)                  // right 400, bottom 300 — touching counts as inside
        val straddling = card(370f, 10f)                  // right 430 > 400
        val outside = card(500f, 500f)
        val innerFrame = frame(20f, 120f, 300f, 150f)     // wholly inside: carried too
        val result = nodesInside(f, listOf(f, inside, onTheEdge, straddling, outside, innerFrame))
        assertEquals(setOf(inside.id, onTheEdge.id, innerFrame.id), result.map { it.id }.toSet())
    }

    @Test
    fun `a frame cannot be shrunk under one card and its margins`() {
        assertEquals(FRAME_MIN_W to FRAME_MIN_H, clampFrameSize(10f, 10f))
        assertEquals(500f to 300f, clampFrameSize(500f, 300f))
    }

    @Test
    fun `a card's box is the strip its text asks for, a frame's its own`() {
        assertEquals(NodeBox(5f, 6f, cardWidth(null), CANVAS_NODE_H), nodeBox(card(5f, 6f)))   // an empty card is its caption's width
        assertEquals(NodeBox(5f, 6f, 400f, 300f), nodeBox(frame(5f, 6f, 400f, 300f)))
    }

    /** The size count (2026-09-20): one line in 48, two in 68, three in 88 — never more; hard breaks count. */
    @Test
    fun `a card grows a line at a time with its text and stops at three`() {
        assertEquals(1, cardLines(null)); assertEquals(1, cardLines("Compost bins"))
        assertEquals(1, cardLines("a".repeat(24))); assertEquals(2, cardLines("a".repeat(25)))   // the wrap width holds 24
        assertEquals(2, cardLines("one\ntwo")); assertEquals(3, cardLines("one\ntwo\nthree\nfour"))
        assertEquals(3, cardLines("x".repeat(500)))
        assertEquals(48f, cardHeight("Compost bins")); assertEquals(68f, cardHeight("one\ntwo")); assertEquals(88f, cardHeight("x".repeat(500)))
        assertEquals(68f, nodeBox(card(0f, 0f).copy(text = "one\ntwo")).h)
    }

    /** The card's width (2026-09-20, `meta-optimizer` over the user's texts): fit to the text between 60 and 220, 8 dp a side; a hand's width wins. */
    @Test
    fun `a card is as wide as its text between the minimum and the wrap width, a hand's width wins`() {
        assertEquals(101f, cardWidth(null)); assertEquals(CANVAS_CARD_MIN_W, cardWidth("Water"))                      // the caption's 10 × 8.5 + 16; 5 × 8.5 + 16 = 58.5 → 60
        assertEquals(135f, cardWidth("Kitchen scraps"))                                                              // 14 × 8.5 + 16
        assertEquals(CANVAS_CARD_WRAP_W, cardWidth("Turn the compost every second week in spring"))                  // capped, then wrapped
        assertEquals(135f, cardWidth("Kitchen scraps\nok"))                                                          // the longest line decides
        assertEquals(24, cardChars(CANVAS_CARD_WRAP_W)); assertEquals(5, cardChars(CANVAS_CARD_MIN_W))
        assertEquals(2, cardLines("Turn the compost every second week in spring"))                                   // 44 chars over 24
        assertEquals(3, cardLines("Turn the compost every second week in spring", width = 100f))                     // a narrower hand width wraps more
        assertEquals(100f, cardWidth("Kitchen scraps", handWidth = 100f)); assertEquals(CANVAS_CARD_MIN_W, cardWidth("x", handWidth = 10f))
        // The stored `width`: the entity's default and zero read as derived, anything else as a hand's.
        assertEquals(null, card(0f, 0f).handWidth()); assertEquals(null, card(0f, 0f).copy(width = CANVAS_LEGACY_CARD_W).handWidth())
        assertEquals(150f, card(0f, 0f).copy(width = 150f).handWidth()); assertEquals(null, frame(0f, 0f, 400f, 300f).handWidth())
        assertEquals(150f, nodeBox(card(0f, 0f).copy(text = "Kitchen scraps", width = 150f)).w)
    }

    @Test
    fun `the fit and the embed height take a frame's box, not a card's`() {
        // One 400 × 300 frame at the origin in a 1000 × 800 px pane at density 1 with a 24 margin.
        val boxes = listOf(nodeBox(frame(0f, 0f, 400f, 300f)))
        val fit = fitToBoxes(boxes, 1000f, 800f, 1f)
        assertEquals(1f, fit.scale, 0.001f)
        assertEquals(300f, fit.panX, 0.001f); assertEquals(250f, fit.panY, 0.001f)
        // The embed: a 400 × 300 board in a 328 column → 280 × 300/400 + 48 = 258.
        assertEquals(258f, embedHeightForBoxes(boxes, 328f), 0.01f)
    }
}
