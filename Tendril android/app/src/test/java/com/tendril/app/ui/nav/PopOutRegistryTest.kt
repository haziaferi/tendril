package com.tendril.app.ui.nav

import com.tendril.app.data.prefs.MapKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** B§13.6 #6 — the remembered half of the pop-out windows: which pages, where. */
class PopOutRegistryTest {

    @Test
    fun `ids encode and decode, garbage dropped, duplicates folded`() {
        assertEquals(listOf(3L, 11L), PopOutRegistry.decode("3,11"))
        assertEquals(listOf(3L), PopOutRegistry.decode("3, x, 3"))
        assertEquals(emptyList<Long>(), PopOutRegistry.decode(null))
        assertEquals("3,11", PopOutRegistry.encode(listOf(3L, 11L)))
    }

    @Test
    fun `add and remove write the key and survive a restart`() {
        val store = MapKeyValueStore()
        val r = PopOutRegistry(store)
        r.add(3); r.add(11); r.add(3)
        assertEquals(listOf(3L, 11L), r.pageIds)
        assertEquals(listOf(3L, 11L), PopOutRegistry(store).pageIds)
        r.remove(3)
        assertEquals("11", store.get(PopOutRegistry.PAGES_KEY))
        r.remove(11)
        assertNull(store.get(PopOutRegistry.PAGES_KEY))
    }

    @Test
    fun `retain drops the pages that no longer exist`() {
        val store = MapKeyValueStore(mapOf(PopOutRegistry.PAGES_KEY to "3,11,12"))
        val r = PopOutRegistry(store)
        r.retain(setOf(11L, 99L))
        assertEquals(listOf(11L), r.pageIds)
        assertEquals("11", store.get(PopOutRegistry.PAGES_KEY))
    }

    @Test
    fun `a new window takes its stored frame, else the cascade from the last, else the default`() {
        val store = MapKeyValueStore()
        val r = PopOutRegistry(store)
        assertEquals(PopOutRegistry.POPOUT_DEFAULT, r.nextFrame(3, last = null))
        assertEquals(WindowFrame(720, 600, 132, 232), r.nextFrame(3, last = WindowFrame(640, 500, 100, 200)))
        assertEquals(PopOutRegistry.POPOUT_DEFAULT, r.nextFrame(3, last = WindowFrame(640, 500, -1, -1)))   // an unplaced last: the platform places
        r.putFrame(3, WindowFrame(900, 700, 10, 20))
        assertEquals(WindowFrame(900, 700, 10, 20), r.nextFrame(3, last = WindowFrame(640, 500, 100, 200)))
        assertEquals("900,700,10,20", store.get(PopOutRegistry.frameKeyFor(3)))
    }
}
