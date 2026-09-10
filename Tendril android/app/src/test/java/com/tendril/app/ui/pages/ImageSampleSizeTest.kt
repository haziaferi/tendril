package com.tendril.app.ui.pages

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The decode budget, which is the difference between a page of photographs rendering and the app
 * being killed for memory.
 *
 * Before this existed `decodeImageBitmap` called `BitmapFactory.decodeByteArray` with no options at
 * all, so a 12-megapixel photograph became 4000 x 3000 x 4 bytes — about 48 MB — in order to be
 * drawn into a box capped at 320dp. Two or three on one page is an OutOfMemoryError. The S4
 * hardware runs never found it because the test image was 96 x 96.
 */
class ImageSampleSizeTest {

    @Test
    fun `a camera photograph is reduced, but not past the budget`() {
        // 4000 wide against a 1600 budget: halving once gives 2000, halving twice gives 1000. The
        // rule keeps 2000 — above the budget rather than below it — because 1000 would be visibly
        // soft on a screen asked to draw it 1440 across. 12 MB instead of 48 MB is the win.
        assertEquals(2, imageSampleSizeFor(4000, 3000, BLOCK_IMAGE_MAX_DIMENSION))
    }

    @Test
    fun `a thumbnail budget reduces the same photograph further`() {
        // The Gallery draws a grid of 100dp covers at once, so it pays for far fewer pixels.
        assertEquals(4, imageSampleSizeFor(4000, 3000, COVER_IMAGE_MAX_DIMENSION))
    }

    @Test
    fun `an image already within budget is not touched`() {
        assertEquals(1, imageSampleSizeFor(800, 600, BLOCK_IMAGE_MAX_DIMENSION))
        assertEquals(1, imageSampleSizeFor(1600, 1200, BLOCK_IMAGE_MAX_DIMENSION))
    }

    @Test
    fun `the longest side is what counts, not the width`() {
        // A tall portrait photograph is as expensive as a wide one and must reduce the same way.
        assertEquals(2, imageSampleSizeFor(3000, 4000, BLOCK_IMAGE_MAX_DIMENSION))
    }

    @Test
    fun `exactly twice the budget still halves once`() {
        // The boundary the `>=` decides: 3200 halves to exactly 1600, which is within budget and
        // loses nothing. A `>` here would have left it at 3200 and paid four times the memory.
        assertEquals(2, imageSampleSizeFor(3200, 2400, BLOCK_IMAGE_MAX_DIMENSION))
    }

    @Test
    fun `a header this build could not read decodes at full size`() {
        // `inJustDecodeBounds` leaves the dimensions at -1 for bytes it cannot parse. Full size is
        // the safe reading of "unknown": it is exactly what happened before any of this existed.
        assertEquals(1, imageSampleSizeFor(-1, -1, BLOCK_IMAGE_MAX_DIMENSION))
        assertEquals(1, imageSampleSizeFor(0, 0, BLOCK_IMAGE_MAX_DIMENSION))
        assertEquals(1, imageSampleSizeFor(4000, 3000, 0))
    }
}
