package com.tendril.app.sync

import com.tendril.app.data.page.Label
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.page.PageLabel
import com.tendril.app.data.pagedatabase.PageDatabase
import com.tendril.app.data.pagedatabase.Property
import com.tendril.app.data.pagedatabase.PropertyType
import com.tendril.app.data.pagedatabase.PropertyValue
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.domain.PurgeRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * §0.6.8 over §9.4 — what a schema on a label adds to the folder: the database's bound label
 * (by name, like a page's own labels), cell values on a page that is not a row, and the hold
 * that keeps those values from being dropped when their database is not legible.
 */
class LabelSchemaSyncTest {

    private companion object {
        const val UID_DB = "11111111-1111-4111-8111-111111111111"
        const val UID_PROP = "22222222-2222-4222-8222-222222222222"
        const val UID_NOTES = "33333333-3333-4333-8333-333333333333"
    }

    /** One device: a store, its DAOs, and an engine over them. */
    private class Device {
        val store = FakePageStore()
        val pageDao = FakePageDao(store)
        val blockDao = FakeBlockDao(store)
        val labelDao = FakeLabelDao(store)
        val pageDatabaseDao = FakePageDatabaseDao(store)
        val propertyDao = FakePropertyDao(store)
        val propertyValueDao = FakePropertyValueDao(store)
        val entryDao = FakeEntryDao()
        val coordinator = RecordingEntryScheduleCoordinator()
        val engine = PagesSyncEngine(
            pageDao = pageDao, blockDao = blockDao, labelDao = labelDao, pageDatabaseDao = pageDatabaseDao,
            propertyDao = propertyDao, propertyValueDao = propertyValueDao, pageDatabaseViewDao = FakePageDatabaseViewDao(store),
            pageCanvasDao = FakePageCanvasDao(store), canvasNodeDao = FakeCanvasNodeDao(store), canvasEdgeDao = FakeCanvasEdgeDao(store),
            pageRelationDao = FakePageRelationDao(store),
            purgeRegistry = PurgeRegistry(FakePurgedRecordDao(), pageDao, entryDao, FakeHabitDao(), propertyDao, coordinator),
            pageContentRepository = PageContentRepository(blockDao, FakePageFtsDao(store)),
        )
    }

    private val at = Instant.ofEpochMilli(1_000L)

    /** Device A: *Books* bound to `#book` with an Author column, and *Notes on Dune* — a plain
     * page under *Reading*, labelled, with an Author. Returns the ids the test needs. */
    private fun seedA(a: Device): Triple<PageDatabase, Long, Long> = runBlocking {
        val dbPage = a.store.seedPage(Page(uid = UID_DB, title = "Books", kind = PageKind.DATABASE, createdAt = at, updatedAt = at))
        val dbId = a.pageDatabaseDao.insert(PageDatabase(pageId = dbPage, createdAt = at, updatedAt = at))
        val author = a.propertyDao.insert(Property(uid = UID_PROP, databaseId = dbId, name = "Author", type = PropertyType.TEXT, order = 0))
        val book = a.labelDao.insert(Label(name = "book"))
        a.pageDatabaseDao.update(a.pageDatabaseDao.getById(dbId)!!.copy(labelId = book, labelConfirmed = true))
        val reading = a.store.seedPage(Page(title = "Reading", kind = PageKind.PAGE, createdAt = at, updatedAt = at))
        val notes = a.store.seedPage(Page(uid = UID_NOTES, title = "Notes on Dune", kind = PageKind.PAGE, parentId = reading, createdAt = at, updatedAt = at))
        a.labelDao.addToPage(PageLabel(pageId = notes, tagId = book))
        a.propertyValueDao.insert(PropertyValue(propertyId = author, rowPageId = notes, value = "Herbert"))
        Triple(a.pageDatabaseDao.getById(dbId)!!, author, notes)
    }

    @Test
    fun `the bound label travels by name and a plain page's values travel with it`() = runBlocking {
        val a = Device(); seedA(a)
        val b = Device()

        val records = a.engine.exportPages()
        val booksRecord = records.single { it.uid == UID_DB }
        assertEquals("book", booksRecord.database?.labelName)
        assertTrue(booksRecord.database?.labelConfirmed == true)
        val notesRecord = records.single { it.uid == UID_NOTES }
        assertNull("not a row", notesRecord.databaseUid)
        assertEquals(listOf(UID_PROP to "Herbert"), notesRecord.propertyValues.map { it.propertyUid to it.value })

        val outcome = b.engine.mergePages(records)

        assertTrue(outcome.quarantined.isEmpty())
        val booksOnB = b.pageDatabaseDao.getByPageId(b.pageDao.getByUid(UID_DB)!!.id)!!
        val bookOnB = b.labelDao.findByName("book")
        assertNotNull("created by name on a device that never had it", bookOnB)
        assertEquals(bookOnB!!.id, booksOnB.labelId)
        assertTrue(booksOnB.labelConfirmed)
        val notesOnB = b.pageDao.getByUid(UID_NOTES)!!
        assertEquals("a member on B too", listOf("Notes on Dune"), b.pageDao.getMembersOf(booksOnB.id, booksOnB.labelId).map { it.title })
        assertEquals("Herbert", b.propertyValueDao.getForRow(notesOnB.id).single().value)
        assertNotNull("and still under Reading", notesOnB.parentId)
    }

    /** §0.6.10 — a review done on one device is done on the other; the later review wins, which
     * is the page record's own LWW, since [com.tendril.app.domain.review.Review.markReviewed]
     * touches the page. */
    @Test
    fun `the last review travels with the database and the later one wins`() = runBlocking {
        val a = Device(); val (booksOnA, _, _) = seedA(a)
        val reviewedAt = at.plusSeconds(3_600)
        a.pageDatabaseDao.update(a.pageDatabaseDao.getById(booksOnA.id)!!.copy(lastReviewedAt = reviewedAt, updatedAt = reviewedAt))
        a.pageDao.touch(booksOnA.pageId, reviewedAt)

        val b = Device()
        b.engine.mergePages(a.engine.exportPages())
        val booksOnB = b.pageDatabaseDao.getByPageId(b.pageDao.getByUid(UID_DB)!!.id)!!
        assertEquals(reviewedAt, booksOnB.lastReviewedAt)

        // B reviews later; A merges B's record and takes the newer time.
        val later = reviewedAt.plusSeconds(86_400)
        b.pageDatabaseDao.update(booksOnB.copy(lastReviewedAt = later, updatedAt = later))
        b.pageDao.touch(booksOnB.pageId, later)
        a.engine.mergePages(b.engine.exportPages())
        assertEquals(later, a.pageDatabaseDao.getById(booksOnA.id)!!.lastReviewedAt)
    }

    @Test
    fun `a v12 peer's database record reads as unbound`() = runBlocking {
        val b = Device()
        val record = PageSnapshotRecord(
            uid = UID_DB, title = "Books", kind = PageKind.DATABASE.name, createdAt = 1_000L, updatedAt = 1_000L,
            database = PageDatabaseSnapshotRecord(properties = listOf(PropertySnapshotRecord(UID_PROP, "Author", "TEXT", order = 0))),
        )

        b.engine.mergePages(listOf(record))

        val books = b.pageDatabaseDao.getByPageId(b.pageDao.getByUid(UID_DB)!!.id)!!
        assertNull(books.labelId)
        assertEquals(false, books.labelConfirmed)
    }

    /**
     * The hold that used to apply only to a row whose own database was withheld. A labelled page
     * has no `databaseUid`, so before §0.6.8 its cells would have been dropped silently when the
     * database's file could not be read — and then republished without them.
     */
    @Test
    fun `a labelled page whose database is unreadable is held, not merged with its values dropped`() = runBlocking {
        val a = Device(); seedA(a)
        val b = Device()
        val records = a.engine.exportPages()

        val outcome = b.engine.mergePages(records.filter { it.uid != UID_DB }, unreadableUids = setOf(UID_DB))

        assertEquals(listOf(UID_NOTES), outcome.quarantined.map { it.uid })
        assertNull("held whole, so nothing of it was written", b.pageDao.getByUid(UID_NOTES))
        // The rest of the batch — Reading — is unaffected.
        assertEquals(listOf("Reading"), b.pageDao.getAll().map { it.title })
    }
}
