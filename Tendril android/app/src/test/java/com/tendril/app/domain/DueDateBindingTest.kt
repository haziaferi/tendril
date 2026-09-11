package com.tendril.app.domain

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.pagedatabase.PageDatabase
import com.tendril.app.data.pagedatabase.Property
import com.tendril.app.data.pagedatabase.PropertyType
import com.tendril.app.data.pagedatabase.PropertyValue
import com.tendril.app.sync.FakeEntryCompletionDao
import com.tendril.app.sync.FakeEntryDao
import com.tendril.app.sync.FakePageDao
import com.tendril.app.sync.FakePageDatabaseDao
import com.tendril.app.sync.FakePageStore
import com.tendril.app.sync.FakePropertyDao
import com.tendril.app.sync.FakePropertyValueDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * §0.8 step 2b — a `DATE` property bound to `Entry.dueDate`, the deadline proper, beside the one
 * that has always bound the When. The two must stay two: seeding, editing through the binding,
 * and unbinding each touch only their own field.
 */
class DueDateBindingTest {

    private val store = FakePageStore()
    private val pageDao = FakePageDao(store)
    private val databaseDao = FakePageDatabaseDao(store)
    private val propertyDao = FakePropertyDao(store)
    private val valueDao = FakePropertyValueDao(store)
    private val entryDao = FakeEntryDao()
    private val completionDao = FakeEntryCompletionDao()
    private val coordinator = object : EntryScheduleCoordinator {
        override suspend fun onEntryChanged(entry: Entry) = Unit
        override suspend fun onEntryRemoved(entry: Entry) = Unit
    }
    private val manager = DatabaseSyncManager(
        pageDao, databaseDao, valueDao, entryDao, completionDao,
        ResolveEntryUseCase(entryDao, completionDao, coordinator),
    )
    private val at = Instant.ofEpochMilli(1_000L)

    private class Fixture(val database: PageDatabase, val done: Long, val date: Long, val due: Long, val row: Long)

    private fun fixture(whenValue: String?, dueValue: String?): Fixture = runBlocking {
        val dbPage = store.seedPage(Page(title = "Errands", kind = PageKind.DATABASE, createdAt = at, updatedAt = at))
        val dbId = databaseDao.insert(PageDatabase(pageId = dbPage, createdAt = at, updatedAt = at))
        val doneId = propertyDao.insert(Property(databaseId = dbId, name = "Done", type = PropertyType.CHECKBOX, order = 0))
        val dateId = propertyDao.insert(Property(databaseId = dbId, name = "Date", type = PropertyType.DATE, order = 1))
        val dueId = propertyDao.insert(Property(databaseId = dbId, name = "Deadline", type = PropertyType.DATE, order = 2))
        val row = store.seedPage(Page(title = "Renew passport", kind = PageKind.PAGE, databaseId = dbId, createdAt = at, updatedAt = at))
        whenValue?.let { valueDao.insert(PropertyValue(propertyId = dateId, rowPageId = row, value = it)) }
        dueValue?.let { valueDao.insert(PropertyValue(propertyId = dueId, rowPageId = row, value = it)) }
        Fixture(databaseDao.getById(dbId)!!, doneId, dateId, dueId, row)
    }

    private fun linked(row: Long): Entry = runBlocking { entryDao.getBySourceRowId(row)!! }

    @Test
    fun `enabling sync seeds the When and the Deadline from their own properties`() = runBlocking {
        val f = fixture(whenValue = "2026-09-12", dueValue = "2026-09-30")

        manager.enableSync(f.database, f.done, f.date, null, listOf(f.row), at, dueDatePropertyId = f.due)

        val entry = linked(f.row)
        assertEquals(LocalDate.of(2026, 9, 12), entry.startDate)
        assertEquals(LocalDate.of(2026, 9, 30), entry.dueDate)
        assertEquals(EntryStatus.PENDING, entry.status)
        // Both bound columns are now live proxies: their stored values were cleared on bind.
        assertTrue(store.propertyValues.values.none { it.propertyId == f.due || it.propertyId == f.date })
    }

    @Test
    fun `a deadline binding without a date binding leaves the When alone`() = runBlocking {
        val f = fixture(whenValue = null, dueValue = "2026-09-30")

        manager.enableSync(f.database, f.done, null, null, listOf(f.row), at, dueDatePropertyId = f.due)

        val entry = linked(f.row)
        assertNull(entry.startDate)
        assertEquals(LocalDate.of(2026, 9, 30), entry.dueDate)
    }

    @Test
    fun `binding a deadline property later seeds dueDate, and unbinding freezes it back`() = runBlocking {
        val f = fixture(whenValue = "2026-09-12", dueValue = "2026-09-30")
        val enabled = manager.enableSync(f.database, f.done, f.date, null, listOf(f.row), at)
        assertNull("the deadline column is unbound so far", linked(f.row).dueDate)

        val bound = manager.bindProperty(enabled, BindingRole.DUE_DATE, f.due, at)
        assertEquals(f.due, bound.dueDatePropertyId)
        assertEquals(LocalDate.of(2026, 9, 30), linked(f.row).dueDate)
        // And the When did not move: two roles, two fields.
        assertEquals(LocalDate.of(2026, 9, 12), linked(f.row).startDate)

        val unbound = manager.unbindProperty(bound, BindingRole.DUE_DATE, at)
        assertNull(unbound.dueDatePropertyId)
        val frozen = store.propertyValues.values.single { it.propertyId == f.due && it.rowPageId == f.row }
        assertEquals("2026-09-30", frozen.value)
    }
}
