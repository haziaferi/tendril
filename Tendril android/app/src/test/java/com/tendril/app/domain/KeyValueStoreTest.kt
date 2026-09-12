package com.tendril.app.domain

import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.pagedatabase.PageDatabase
import com.tendril.app.data.prefs.MapKeyValueStore
import com.tendril.app.domain.review.Review
import com.tendril.app.domain.review.ReviewItem
import com.tendril.app.sync.FakeEntryCompletionDao
import com.tendril.app.sync.FakeEntryDao
import com.tendril.app.sync.FakeHabitCompletionDao
import com.tendril.app.sync.FakePageDao
import com.tendril.app.sync.FakePageDatabaseDao
import com.tendril.app.sync.FakePageStore
import com.tendril.app.sync.FakeTimeLogDao
import com.tendril.app.ui.calendar.CalendarLayers
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** §0.10 item 12 — the store itself, the layers' round trip, and Review's cadence override. */
class KeyValueStoreTest {

    @Test
    fun `put, get, remove, and the typed helpers`() {
        val store = MapKeyValueStore()
        assertNull(store.get("x"))
        store.put("x", "1")
        assertEquals("1", store.get("x"))
        assertEquals(1, store.getInt("x", 9)); assertEquals(9, store.getInt("missing", 9))
        store.putBoolean("b", true)
        assertTrue(store.getBoolean("b", false)); assertTrue(store.getBoolean("nope", true))
        store.put("x", null)
        assertNull(store.get("x"))
    }

    @Test
    fun `observe sees the value now and every later put`() {
        val store = MapKeyValueStore(mapOf("k" to "a"))
        val flow = store.observe("k")
        assertEquals("a", flow.value)
        store.put("k", "b")
        assertEquals("b", flow.value)
        store.put("k", null)
        assertNull(flow.value)
    }

    @Test
    fun `persist is handed the whole map after every put`() {
        val seen = mutableListOf<Map<String, String>>()
        val store = object : MapKeyValueStore() { override fun persist(all: Map<String, String>) { seen += all } }
        store.put("a", "1"); store.put("b", "2"); store.put("a", null)
        assertEquals(listOf(mapOf("a" to "1"), mapOf("a" to "1", "b" to "2"), mapOf("b" to "2")), seen)
    }

    @Test
    fun `calendar layers round-trip through four letters, and default when absent`() {
        assertEquals(CalendarLayers(), CalendarLayers.decode(null))
        val chosen = CalendarLayers(tasks = false, events = true, habits = true, databaseDates = false)
        assertEquals("eh", chosen.encode())
        assertEquals(chosen, CalendarLayers.decode(chosen.encode()))
        assertEquals(CalendarLayers(false, false, false, false), CalendarLayers.decode(""))
    }

    @Test
    fun `review reads its cadence from the store`() = runBlocking {
        val pages = FakePageStore()
        val at = Instant.parse("2026-09-12T12:00:00Z")
        val dbPage = pages.seedPage(Page(title = "Books", kind = PageKind.DATABASE, createdAt = at, updatedAt = at))
        val databaseDao = FakePageDatabaseDao(pages)
        databaseDao.insert(PageDatabase(pageId = dbPage, lastReviewedAt = at.minus(Duration.ofDays(3)), createdAt = at, updatedAt = at))
        val store = MapKeyValueStore()
        val review = Review(
            FakePageDao(pages), databaseDao, FakeEntryDao(), FakeEntryCompletionDao(), FakeHabitCompletionDao(), FakeTimeLogDao(),
            mockk(relaxed = true), mockk(relaxed = true), store,
        )
        val today = LocalDate.of(2026, 9, 12)
        assertTrue("reviewed three days ago: not due on the default week", review.items(at, today).isEmpty())
        store.putInt(Review.CADENCE_DAYS_KEY, 2)
        assertEquals(2, review.cadence.toDays().toInt())
        assertTrue("due on a two-day cadence", review.items(at, today).single() is ReviewItem.DatabaseDue)
    }
}
