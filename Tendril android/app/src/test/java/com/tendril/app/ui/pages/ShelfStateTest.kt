package com.tendril.app.ui.pages

import com.tendril.app.data.prefs.MapKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 14h·1 — what the shelf holds, remembered; what it shows beside a given page; its clamp; the swap. */
class ShelfStateTest {

    @Test
    fun `a shelf encodes and decodes, and garbage is a closed shelf`() {
        assertEquals(Shelf.Page(42), Shelf.decode(Shelf.Page(42).encode()))
        assertEquals(Shelf.Graph, Shelf.decode(Shelf.Graph.encode()))
        assertEquals(Shelf.Journal, Shelf.decode(Shelf.Journal.encode()))
        assertEquals("page:42", Shelf.Page(42).encode())
        assertNull(Shelf.decode(null))
        assertNull(Shelf.decode(""))
        assertNull(Shelf.decode("page:"))
        assertNull(Shelf.decode("page:abc"))
        assertNull(Shelf.decode("window"))
    }

    @Test
    fun `open and close write the store, and a relaunch reads what was open`() {
        val store = MapKeyValueStore()
        val state = ShelfState(store)
        assertNull(state.content)
        state.open(Shelf.Page(7))
        assertEquals("page:7", store.get(ShelfState.CONTENT_KEY))
        assertEquals(Shelf.Page(7), ShelfState(store).content)
        state.close()
        assertNull(store.get(ShelfState.CONTENT_KEY))
        assertNull(ShelfState(store).content)
    }

    @Test
    fun `the chord closes an open shelf and reopens the last one, across a relaunch too`() {
        val store = MapKeyValueStore()
        val state = ShelfState(store)
        state.toggle()                                  // nothing to reopen yet
        assertNull(state.content)
        state.open(Shelf.Journal)
        state.toggle()
        assertNull(state.content)
        state.toggle()
        assertEquals(Shelf.Journal, state.content)
        state.close()
        val again = ShelfState(store)                   // closed at quit; the chord still knows the last
        assertNull(again.content)
        again.toggle()
        assertEquals(Shelf.Journal, again.content)
    }

    @Test
    fun `nothing at the tab root, and a page never sits beside itself`() {
        val state = ShelfState(MapKeyValueStore())
        state.open(Shelf.Page(3))
        assertNull(state.shown(openPageId = null, journalPageId = null))
        assertEquals(Shelf.Page(3), state.shown(openPageId = 9, journalPageId = null))
        assertEquals(Shelf.Graph, state.shown(openPageId = 3, journalPageId = null))
        state.open(Shelf.Journal)
        assertEquals(Shelf.Journal, state.shown(openPageId = 9, journalPageId = null))    // unresolved: still the Journal
        assertEquals(Shelf.Journal, state.shown(openPageId = 9, journalPageId = 11))
        assertEquals(Shelf.Graph, state.shown(openPageId = 11, journalPageId = 11))     // today's page is the open page
        state.open(Shelf.Graph)
        assertEquals(Shelf.Graph, state.shown(openPageId = 11, journalPageId = 11))
    }

    @Test
    fun `the width is remembered within its range and clamped to 45 percent of the workspace`() {
        val store = MapKeyValueStore()
        val state = ShelfState(store)
        assertEquals(380, state.width.widthDp)
        state.width.resizeTo(900)
        assertEquals(560, state.width.widthDp)
        state.width.resizeTo(100)
        assertEquals(280, state.width.widthDp)
        state.width.resizeTo(420)
        assertEquals(420, ShelfState(store).width.widthDp)
        assertEquals(420, state.effectiveWidthDp(1200f))       // 45 % of 1200 = 540: the width stands
        assertEquals(378, state.effectiveWidthDp(840f))        // 45 % of 840: the clamp
    }

    @Test
    fun `swap puts the main page in the shelf and hands back the page to show`() {
        val store = MapKeyValueStore()
        val state = ShelfState(store)
        state.open(Shelf.Journal)
        assertEquals(11L, state.swap(mainPageId = 3, shelfPageId = 11))
        assertEquals(Shelf.Page(3), state.content)             // a swapped-out Journal is that day's page, not "today"
        assertEquals("page:3", store.get(ShelfState.CONTENT_KEY))
    }
}
