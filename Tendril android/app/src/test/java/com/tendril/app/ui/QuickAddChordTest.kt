package com.tendril.app.ui

import com.tendril.app.ui.nav.QuickAddChord
import org.junit.Assert.assertEquals
import org.junit.Test

/** B§13.6 #7 — the pick list's Win32 halves, so a wrong bit never reaches `RegisterHotKey`. */
class QuickAddChordTest {
    @Test
    fun `each chord's mask and key are Win32's`() {
        assertEquals(0x0001 or 0x0008 or 0x4000, QuickAddChord.WIN_ALT_N.modifiersMask())
        assertEquals(0x4E, QuickAddChord.WIN_ALT_N.vk)
        assertEquals(0x51, QuickAddChord.WIN_ALT_Q.vk)
        assertEquals(0x0002 or 0x0004 or 0x4000, QuickAddChord.CTRL_SHIFT_SPACE.modifiersMask())
        assertEquals(0x20, QuickAddChord.CTRL_SHIFT_SPACE.vk)
        assertEquals(0x0001 or 0x0004 or 0x4000, QuickAddChord.SHIFT_ALT_A.modifiersMask())
        assertEquals(0x0001 or 0x0002 or 0x4000, QuickAddChord.CTRL_ALT_SPACE.modifiersMask())
    }

    @Test
    fun `an absent or unknown key is the default, a stored one its chord`() {
        assertEquals(QuickAddChord.WIN_ALT_N, QuickAddChord.fromKey(null))
        assertEquals(QuickAddChord.WIN_ALT_N, QuickAddChord.fromKey("garbage"))
        assertEquals(QuickAddChord.SHIFT_ALT_A, QuickAddChord.fromKey("shift_alt_a"))
    }

    @Test
    fun `keys and labels are unique`() {
        assertEquals(QuickAddChord.entries.size, QuickAddChord.entries.map { it.key }.toSet().size)
        assertEquals(QuickAddChord.entries.size, QuickAddChord.entries.map { it.label }.toSet().size)
    }
}
