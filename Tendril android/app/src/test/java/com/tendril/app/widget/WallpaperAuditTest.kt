package com.tendril.app.widget

import com.tendril.app.ui.theme.Register
import com.tendril.app.ui.theme.paletteFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** §8.6 resolved (2026-09-22) — the readout on the real wallpaper, and the opacity it suggests. */
class WallpaperAuditTest {

    private val register = Register.INK
    private fun roles(dark: Boolean): (ContrastRole) -> Rgb {
        val p = paletteFor(register, dark)
        return { role ->
            when (role) {
                ContrastRole.TEXT -> p.text
                ContrastRole.TEXT_DIM -> p.textDim
                ContrastRole.TEXT_FAINT -> p.textFaint
                ContrastRole.ACCENT, ContrastRole.ACCENT2 -> p.accent
            }.toRgb()
        }
    }

    @Test
    fun `on one wallpaper the ratio is the composite's, never the bracket's minimum`() {
        val bg = paletteFor(register, dark = false).bg.toRgb()
        val text = paletteFor(register, dark = false).text.toRgb()
        val white = Rgb(255, 255, 255)
        val onWhite = wallpaperContrast(text, bg, opacityPct = 20, wallpaper = white)
        assertTrue(onWhite > worstCaseContrast(text, bg, 20))                       // the bracket's black side is the worst case for light text ground
        assertEquals(contrastRatio(text, compositeRgb(bg, white, 20)), onWhite, 1e-9)
    }

    @Test
    fun `a light widget at full opacity passes every role on any wallpaper, and the suggestion is the smallest that does`() {
        val bg = paletteFor(register, dark = false).bg.toRgb()
        val black = Rgb(0, 0, 0)
        val rows = auditOnWallpaper(register, false, bg, 100, WidgetPrefKeys.DEFAULT_SHADE, WidgetPrefKeys.DEFAULT_HUE_OFFSET, black, roles(false))
        assertEquals(ContrastRole.entries.size, rows.size)
        assertTrue(rows.all { it.pass })
        val suggested = suggestedOpacity(register, false, bg, WidgetPrefKeys.DEFAULT_SHADE, WidgetPrefKeys.DEFAULT_HUE_OFFSET, black, roles(false))!!
        assertTrue(suggested in 0..100 && suggested % 5 == 0)
        assertTrue(auditOnWallpaper(register, false, bg, suggested, WidgetPrefKeys.DEFAULT_SHADE, WidgetPrefKeys.DEFAULT_HUE_OFFSET, black, roles(false)).all { it.pass })
        if (suggested > 0) assertTrue(auditOnWallpaper(register, false, bg, suggested - 5, WidgetPrefKeys.DEFAULT_SHADE, WidgetPrefKeys.DEFAULT_HUE_OFFSET, black, roles(false)).any { !it.pass })
    }

    @Test
    fun `a light widget on a white wallpaper needs less opacity than on a black one`() {
        val bg = paletteFor(register, dark = false).bg.toRgb()
        val onWhite = suggestedOpacity(register, false, bg, WidgetPrefKeys.DEFAULT_SHADE, WidgetPrefKeys.DEFAULT_HUE_OFFSET, Rgb(255, 255, 255), roles(false))!!
        val onBlack = suggestedOpacity(register, false, bg, WidgetPrefKeys.DEFAULT_SHADE, WidgetPrefKeys.DEFAULT_HUE_OFFSET, Rgb(0, 0, 0), roles(false))!!
        assertTrue("$onWhite < $onBlack", onWhite < onBlack)
    }

    @Test
    fun `an impossible combination suggests nothing`() {
        // A role whose foreground is the wallpaper's own colour can never pass at any opacity.
        val bg = Rgb(128, 128, 128)
        val grey = Rgb(128, 128, 128)
        assertNull(suggestedOpacity(register, true, bg, 0, 0, grey, roleRgb = { grey }))
    }
}
