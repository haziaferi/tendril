package com.tendril.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * audit 4.3 — checkbox-only mode must refuse to turn on while App Lock is on.
 *
 * The two features contradict each other outright: App Lock exists to put authentication in
 * front of the app, and this mode's entire purpose is to draw a page over the keyguard without
 * any. Previously `activate` just set the field, so whichever the person enabled second won,
 * and enabling this one second silently defeated the other.
 *
 * The other half of 4.3 — the bottom bar rendering over the keyguard, which let a tap reach
 * Settings — is in `WorkbenchScaffold` and is Compose, so it is not reachable from a unit test.
 * It is a one-line condition (`if (!bypassingKeyguard)`) tied to the same flag that drives the
 * window flags, deliberately so that the control and the bypass cannot get out of step.
 */
class CheckboxOnlyStateTest {

    @Test
    fun `activates normally when App Lock is off`() {
        val state = CheckboxOnlyState { false }

        assertTrue("activation should be allowed", state.activate(7L))
        assertEquals(7L, state.activePageId.value)
    }

    @Test
    fun `refuses to activate while App Lock is on`() {
        val state = CheckboxOnlyState { true }

        assertFalse("activation must be refused", state.activate(7L))
        assertNull("and nothing should have been recorded", state.activePageId.value)
    }

    @Test
    fun `the default is no App Lock, for platforms that have none`() {
        // Desktop has no App Lock at all (§1), so the predicate defaults to "off" rather than
        // forcing every construction site to say so.
        val state = CheckboxOnlyState()
        assertTrue(state.activate(7L))
    }

    @Test
    fun `App Lock is consulted at activation, not at construction`() {
        // The Android wiring passes `{ appLockPreferences.enabled.value }`, so the answer can
        // change during the session — turning App Lock on in Settings has to take effect without
        // the container being rebuilt.
        var locked = false
        val state = CheckboxOnlyState { locked }

        assertTrue(state.activate(1L))
        state.deactivate()

        locked = true
        assertFalse("the later value must be the one that counts", state.activate(1L))
    }

    @Test
    fun `turning it off is never blocked`() {
        // The guard is only about *entering* the bypass. If App Lock somehow becomes enabled
        // while the mode is already on, the escape route must not be barred as well.
        var locked = false
        val state = CheckboxOnlyState { locked }
        state.activate(3L)

        locked = true
        state.deactivate()

        assertNull(state.activePageId.value)
    }

    @Test
    fun `activating another page replaces the first`() {
        // Unchanged behaviour, pinned because the window flags are window-level rather than
        // per-screen: two pages bypassing the keyguard at once is not a state that can exist.
        val state = CheckboxOnlyState { false }

        state.activate(1L)
        state.activate(2L)

        assertEquals(2L, state.activePageId.value)
    }

    @Test
    fun `a refusal leaves an already-active page alone`() {
        var locked = false
        val state = CheckboxOnlyState { locked }
        state.activate(1L)

        locked = true
        assertFalse(state.activate(2L))
        assertEquals("the refusal must not clear what was already active", 1L, state.activePageId.value)
    }
}
