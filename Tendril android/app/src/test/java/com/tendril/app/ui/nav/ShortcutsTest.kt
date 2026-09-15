package com.tendril.app.ui.nav

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 14e — the binding table: one chord per action, resolved exactly, listed as bound. */
class ShortcutsTest {
    @Test
    fun `each chord resolves to its action`() {
        assertEquals(ShortcutAction.SWITCHER, shortcutFor(Key.K, ctrl = true, shift = false))
        assertEquals(ShortcutAction.TAB_TASKS, shortcutFor(Key.Three, ctrl = true, shift = false))
        assertEquals(ShortcutAction.BACK, shortcutFor(Key.DirectionLeft, ctrl = false, shift = false, alt = true))
        assertEquals(ShortcutAction.SHORTCUTS, shortcutFor(Key.F1, ctrl = false, shift = false))
        assertNull("Ctrl+← is not back — a text field's word jump", shortcutFor(Key.DirectionLeft, ctrl = true, shift = false))
    }

    @Test
    fun `shift is part of the chord, so Ctrl+N and Ctrl+Shift+N are two actions`() {
        assertEquals(ShortcutAction.NEW_PAGE, shortcutFor(Key.N, ctrl = true, shift = false))
        assertEquals(ShortcutAction.NEW_TASK, shortcutFor(Key.N, ctrl = true, shift = true))
        assertNull("Ctrl+Shift+K is nothing", shortcutFor(Key.K, ctrl = true, shift = true))
    }

    @Test
    fun `without its modifier a key is text or type-ahead, never a shortcut`() {
        assertNull(shortcutFor(Key.N, ctrl = false, shift = false))
        assertNull(shortcutFor(Key.DirectionLeft, ctrl = false, shift = false))
        assertNull("F1 with Ctrl is nothing", shortcutFor(Key.F1, ctrl = true, shift = false))
    }

    @Test
    fun `every action is bound exactly once and every chord is unique`() {
        assertEquals(ShortcutAction.entries.toSet(), SHORTCUTS.map { it.first }.toSet())
        assertEquals(SHORTCUTS.size, SHORTCUTS.map { it.first }.distinct().size)
        assertEquals(SHORTCUTS.size, SHORTCUTS.map { it.second }.distinct().size)
        assertEquals("Ctrl+Shift+N", SHORTCUTS.first { it.first == ShortcutAction.NEW_TASK }.second.label())
    }
}
