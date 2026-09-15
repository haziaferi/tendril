package com.tendril.app.ui.pages

import com.tendril.app.data.prefs.MapKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 14c — the tree pane's width and collapsed flag are device preferences; its expansions are not. */
class PagesTreeStateTest {

    @Test
    fun `defaults, then a width that survives a restart and is clamped`() {
        val store = MapKeyValueStore()
        val state = PagesTreeState(store)
        assertEquals(280, state.widthDp)
        assertFalse(state.collapsed)
        state.resizeTo(1000)
        assertEquals(480, state.widthDp)
        state.resizeTo(10)
        assertEquals(200, state.widthDp)
        state.resizeTo(333)
        assertEquals(333, PagesTreeState(store).widthDp)     // a second start reads the store
    }

    @Test
    fun `collapsed toggles and persists`() {
        val store = MapKeyValueStore()
        val state = PagesTreeState(store)
        state.toggle()
        assertTrue(state.collapsed)
        assertTrue(PagesTreeState(store).collapsed)
        state.toggle()
        assertFalse(PagesTreeState(store).collapsed)
    }

    @Test
    fun `a stored width outside the range is clamped on read`() {
        val store = MapKeyValueStore(mapOf(PagesTreeState.WIDTH_KEY to "9999"))
        assertEquals(480, PagesTreeState(store).widthDp)
    }

    @Test
    fun `expansions are a session's and never stored`() {
        val store = MapKeyValueStore()
        val state = PagesTreeState(store)
        state.toggleExpanded(4)
        assertTrue(4L in state.expanded)
        state.toggleExpanded(4)
        assertFalse(4L in state.expanded)
        assertEquals(null, store.get(PagesTreeState.WIDTH_KEY))      // nothing written for an expansion
    }
}
