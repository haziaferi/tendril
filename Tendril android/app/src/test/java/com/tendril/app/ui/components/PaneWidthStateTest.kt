package com.tendril.app.ui.components

import com.tendril.app.data.prefs.MapKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Test

/** 14f·1 — a pane's width: clamped, remembered, defaulted. */
class PaneWidthStateTest {
    @Test
    fun `starts at the default and clamps a resize to the bounds`() {
        val store = MapKeyValueStore()
        val state = PaneWidthState(store, "k", defaultDp = 420, minDp = 300, maxDp = 600)
        assertEquals(420, state.widthDp)
        state.resizeTo(900); assertEquals(600, state.widthDp)
        state.resizeTo(10); assertEquals(300, state.widthDp)
    }

    @Test
    fun `a stored width survives, clamped on read`() {
        val store = MapKeyValueStore()
        PaneWidthState(store, "k", 420, 300, 600).resizeTo(480)
        assertEquals(480, PaneWidthState(store, "k", 420, 300, 600).widthDp)
        store.putInt("k", 5000)
        assertEquals(600, PaneWidthState(store, "k", 420, 300, 600).widthDp)
    }
}
