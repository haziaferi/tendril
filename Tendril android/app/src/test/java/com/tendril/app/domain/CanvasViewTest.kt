package com.tendril.app.domain

import com.tendril.app.domain.canvas.contentAtPaneCentre
import com.tendril.app.domain.canvas.fitToCards
import org.junit.Assert.assertEquals
import org.junit.Test

/** L9 + L10 — the canvas's Fit and the new card's centre placement. Content in dp, the pane in px. */
class CanvasViewTest {
    private val w = 180f; private val h = 90f

    @Test
    fun `one card fits at 1x, centred`() {
        val fit = fitToCards(listOf(100f to 50f), w, h, paneWidthPx = 1000f, paneHeightPx = 600f, density = 1f)
        assertEquals(1f, fit.scale, 1e-4f)
        // the card's centre (190, 95) lands on the pane's centre (500, 300)
        assertEquals(500f, 190f * fit.scale + fit.panX, 1e-3f)
        assertEquals(300f, 95f * fit.scale + fit.panY, 1e-3f)
    }

    @Test
    fun `cards wider than the pane scale down to fit with the margin`() {
        val fit = fitToCards(listOf(0f to 0f, 1820f to 0f), w, h, paneWidthPx = 1000f, paneHeightPx = 600f, density = 1f, marginDp = 24f)
        assertEquals((1000f - 48f) / 2000f, fit.scale, 1e-4f)
        assertEquals(24f, 0f * fit.scale + fit.panX, 1e-3f)          // the left edge at the margin
        assertEquals(976f, 2000f * fit.scale + fit.panX, 1e-3f)      // the right edge at the margin
    }

    @Test
    fun `cards taller than wide scale by the height, and density counts`() {
        val fit = fitToCards(listOf(0f to 0f, 0f to 910f), w, h, paneWidthPx = 1000f, paneHeightPx = 600f, density = 2f)
        assertEquals((600f - 96f) / 2000f, fit.scale, 1e-4f)
    }

    @Test
    fun `no cards is the identity`() {
        assertEquals(com.tendril.app.domain.canvas.CanvasFit(1f, 0f, 0f), fitToCards(emptyList(), w, h, 1000f, 600f, 1f))
    }

    @Test
    fun `a card placed at the centre reads back at the pane's centre`() {
        val (x, y) = contentAtPaneCentre(1000f, 600f, scale = 0.5f, panX = 120f, panY = -40f, density = 2f, nodeW = w, nodeH = h)
        // forward: screen = content × density × scale + pan, for the card's centre
        assertEquals(500f, (x + w / 2f) * 2f * 0.5f + 120f, 1e-3f)
        assertEquals(300f, (y + h / 2f) * 2f * 0.5f - 40f, 1e-3f)
    }
}
