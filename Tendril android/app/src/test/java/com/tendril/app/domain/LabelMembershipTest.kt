package com.tendril.app.domain

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.page.Label
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.pagedatabase.PageDatabase
import com.tendril.app.data.pagedatabase.Property
import com.tendril.app.data.pagedatabase.PropertyType
import com.tendril.app.data.pagedatabase.PropertyValue
import com.tendril.app.sync.FakeEntryCompletionDao
import com.tendril.app.sync.FakeEntryDao
import com.tendril.app.sync.FakeLabelDao
import com.tendril.app.sync.FakePageDao
import com.tendril.app.sync.FakePageDatabaseDao
import com.tendril.app.sync.FakePageStore
import com.tendril.app.sync.FakePropertyDao
import com.tendril.app.sync.FakePropertyValueDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * §0.6.8 — schema on a label, the Task half. Membership itself is a query
 * ([com.tendril.app.data.page.PageDao.getMembersOf]) and is covered here only through the fake;
 * what these tests pin is the acceptance sentence: a plain page anywhere can be labelled into a
 * database, unlabelled and relabelled *without loss*, and a page is one task however many
 * databases show it.
 */
class LabelMembershipTest {

    private val store = FakePageStore()
    private val pageDao = FakePageDao(store)
    private val databaseDao = FakePageDatabaseDao(store)
    private val propertyDao = FakePropertyDao(store)
    private val valueDao = FakePropertyValueDao(store)
    private val labelDao = FakeLabelDao(store)
    private val entryDao = FakeEntryDao()
    private val completionDao = FakeEntryCompletionDao()
    private val coordinator = object : EntryScheduleCoordinator {
        override suspend fun onEntryChanged(entry: Entry) = Unit
        override suspend fun onEntryRemoved(entry: Entry) = Unit
    }
    private val resolve = ResolveEntryUseCase(entryDao, completionDao, coordinator)
    private val syncManager = DatabaseSyncManager(pageDao, databaseDao, valueDao, entryDao, completionDao, resolve, coordinator)
    private val membership = LabelMembership(pageDao, databaseDao, labelDao, entryDao, syncManager, resolve)
    private val at = Instant.ofEpochMilli(1_000L)

    /** A database with a Done checkbox, syncing or not, and the label it binds. */
    private fun database(title: String, label: String, syncing: Boolean): PageDatabase = runBlocking {
        val dbPage = store.seedPage(Page(title = title, kind = PageKind.DATABASE, createdAt = at, updatedAt = at))
        val dbId = databaseDao.insert(PageDatabase(pageId = dbPage, createdAt = at, updatedAt = at))
        val doneId = propertyDao.insert(Property(databaseId = dbId, name = "Done", type = PropertyType.CHECKBOX, order = 0))
        propertyDao.insert(Property(databaseId = dbId, name = "Author", type = PropertyType.TEXT, order = 1))
        val labelId = labelDao.insert(Label(name = label))
        val db = databaseDao.getById(dbId)!!.copy(labelId = labelId, syncToTasks = syncing, donePropertyId = if (syncing) doneId else null)
        databaseDao.update(db)
        db
    }

    private fun label(name: String): Label = runBlocking { labelDao.findByName(name)!! }
    private fun plainPage(title: String): Long = store.seedPage(Page(title = title, kind = PageKind.PAGE, createdAt = at, updatedAt = at))
    private fun live(pageId: Long): Entry? = runBlocking { entryDao.getBySourceRowId(pageId) }
    private fun members(db: PageDatabase): List<String> = runBlocking { pageDao.getMembersOf(db.id, db.labelId).map { it.title } }

    /** Audit 2026-09-24 5a.6 — re-binding the label a database already has changed nothing, and
     * still claimed an edit to the database page (§9.4). */
    @Test
    fun `binding the label a database already has moves no timestamp`() = runBlocking {
        val books = database("Books", "book", syncing = false)
        membership.bindLabel(books, books.labelId!!, at.plusSeconds(60))
        assertEquals(at, pageDao.getById(books.pageId)!!.updatedAt)
        assertEquals(at, databaseDao.getById(books.id)!!.updatedAt)
    }

    @Test
    fun `a labelled page is a member beside the native rows, and stays where it lives`() = runBlocking {
        val books = database("Books", "book", syncing = false)
        store.seedPage(Page(title = "Dune", kind = PageKind.PAGE, databaseId = books.id, createdAt = at, updatedAt = at))
        val reading = plainPage("Reading")
        val notes = store.seedPage(Page(title = "Notes on Dune", kind = PageKind.PAGE, parentId = reading, createdAt = at, updatedAt = at))

        assertTrue(membership.applyLabel(notes, label("book"), at))

        assertEquals(listOf("Dune", "Notes on Dune"), members(books))
        assertEquals(reading, pageDao.getById(notes)!!.parentId)
        assertNull("no Sync to Tasks, so no task", live(notes))
        // Applying it again is a no-op the caller can tell apart, so it claims no authorship.
        assertFalse(membership.applyLabel(notes, label("book"), at))
    }

    @Test
    fun `unlabelling hides the values and relabelling shows them again`() = runBlocking {
        val books = database("Books", "book", syncing = false)
        val author = propertyDao.getForDatabase(books.id).first { it.name == "Author" }
        val notes = plainPage("Notes on Dune")
        membership.applyLabel(notes, label("book"), at)
        valueDao.insert(PropertyValue(propertyId = author.id, rowPageId = notes, value = "Herbert"))

        membership.removeLabel(notes, label("book"), at)
        assertEquals(emptyList<String>(), members(books))
        // Hidden, not deleted (§0.6.8, B§12.5): the value is still on the page.
        assertEquals("Herbert", valueDao.getForPropertyAndRow(author.id, notes)?.value)

        membership.applyLabel(notes, label("book"), at)
        assertEquals(listOf("Notes on Dune"), members(books))
        assertEquals("Herbert", valueDao.getForPropertyAndRow(author.id, notes)?.value)
    }

    @Test
    fun `a label into a syncing database makes the page a task, asked once`() = runBlocking {
        val todo = database("To-do", "task", syncing = true)
        val notes = plainPage("Call the bank")

        assertEquals(todo.id, membership.needsConfirmation(label("task"))?.id)
        val confirmed = membership.confirm(todo, at)
        assertTrue(confirmed.labelConfirmed)
        assertNull("never asked again", membership.needsConfirmation(label("task")))

        membership.applyLabel(notes, label("task"), at)
        val task = live(notes)
        assertNotNull(task)
        assertEquals("Call the bank", task!!.title)
        assertEquals(EntryStatus.PENDING, task.status)
    }

    @Test
    fun `removing the label trashes the task, and relabelling brings the same task back`() = runBlocking {
        val todo = database("To-do", "task", syncing = true)
        val notes = plainPage("Call the bank")
        membership.applyLabel(notes, label("task"), at)
        val task = live(notes)!!
        resolve.resolve(task.id, EntryStatus.DONE, at)

        membership.removeLabel(notes, label("task"), at)
        assertNull(live(notes))
        assertNotNull("in Trash, not gone", entryDao.getTrashedBySourceRowId(notes))

        membership.applyLabel(notes, label("task"), at)
        val back = live(notes)!!
        assertEquals("the same Entry, not a fresh one", task.uid, back.uid)
        assertEquals("with its state", EntryStatus.DONE, back.status)
        assertEquals("and only one live task for the page", 1, entryDao.getAll().count { it.sourceRowId == notes && it.deletedAt == null })
    }

    @Test
    fun `a page in two syncing databases is one task, kept while either membership remains`() = runBlocking {
        val todo = database("To-do", "task", syncing = true)
        val goals = database("Goals", "goal", syncing = true)
        val notes = plainPage("Run a marathon")

        membership.applyLabel(notes, label("task"), at)
        membership.applyLabel(notes, label("goal"), at)
        assertEquals(1, entryDao.getAll().count { it.sourceRowId == notes && it.deletedAt == null })
        assertEquals(listOf("Run a marathon"), members(todo))
        assertEquals(listOf("Run a marathon"), members(goals))

        membership.removeLabel(notes, label("task"), at)
        assertNotNull("Goals still shows it, so it is still a task", live(notes))

        membership.removeLabel(notes, label("goal"), at)
        assertNull(live(notes))
    }

    @Test
    fun `a native row keeps its task when the label it also carries is removed`() = runBlocking {
        val todo = database("To-do", "task", syncing = true)
        val row = store.seedPage(Page(title = "Native", kind = PageKind.PAGE, databaseId = todo.id, createdAt = at, updatedAt = at))
        syncManager.enableSync(todo, todo.donePropertyId!!, null, null, listOf(row), at)
        membership.applyLabel(row, label("task"), at)
        assertEquals("listed once", listOf("Native"), members(todo))

        membership.removeLabel(row, label("task"), at)

        assertNotNull("its home syncs; the label added nothing and takes nothing", live(row))
    }

    @Test
    fun `binding a label to a syncing database makes the labelled pages tasks, unbinding retires them`() = runBlocking {
        val todo = database("To-do", "task", syncing = true)
        val other = labelDao.insert(Label(name = "errand"))
        val a = plainPage("Post office")
        val b = plainPage("Pharmacy")
        for (p in listOf(a, b)) labelDao.addToPage(com.tendril.app.data.page.PageLabel(pageId = p, tagId = other))
        val native = store.seedPage(Page(title = "Native", kind = PageKind.PAGE, databaseId = todo.id, createdAt = at, updatedAt = at))
        syncManager.enableSync(todo, todo.donePropertyId!!, null, null, listOf(native), at)

        val bound = membership.bindLabel(todo, other, at)
        assertEquals(other, bound.labelId)
        // Members are ordered by id — the tree order of creation — not natives first.
        assertEquals(listOf("Post office", "Pharmacy", "Native"), members(bound))
        assertNotNull(live(a)); assertNotNull(live(b))

        val unbound = membership.unbindLabel(bound, at)
        assertNull(unbound.labelId)
        assertFalse("the once-only answer belongs to the binding", unbound.labelConfirmed)
        assertEquals(listOf("Native"), members(unbound))
        assertNull(live(a)); assertNull(live(b))
        assertNotNull("native rows are not the label's to retire", live(native))
    }

    @Test
    fun `a binding change crystallises a labelled member like a native row`() = runBlocking {
        // DatabaseSyncManager iterates members, not native rows — the one line that makes every
        // §5.2.1 operation apply to a labelled page too.
        val todo = database("To-do", "task", syncing = true)
        val notes = plainPage("Call the bank")
        membership.applyLabel(notes, label("task"), at)
        resolve.resolve(live(notes)!!.id, EntryStatus.DONE, at)

        val off = syncManager.disableSync(todo, at)

        assertFalse(off.syncToTasks)
        assertNull("its task went to Trash with everyone else's", live(notes))
    }
}
