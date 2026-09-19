package com.tendril.app.sync

import com.tendril.app.data.page.PageDao
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Regression tests for SYNC-01 — silent data loss during ordinary sync.
 *
 * A Syncthing conflict sibling (`entries_active.sync-conflict-<date>-<device>.json`) is the copy
 * of the data that *lost* the race. The sweep in [SnapshotSyncOrchestrator.readAndMerge] used to
 * delete every one of them unconditionally, after a merge attempt that fails soft: an
 * undecryptable file decrypts to `""`, a corrupt one decodes to null, and an unrecognised name
 * fell through the `when` entirely — all three looked identical to "empty file, nothing to do."
 *
 * The rule these tests pin: **a conflict file is deleted only if its contents actually merged.**
 */
class SnapshotSyncConflictTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun orchestrator(entryDao: FakeEntryDao, habitDao: FakeHabitDao) =
        SnapshotSyncOrchestrator(
            entryDao = entryDao,
            habitDao = habitDao,
            // Neither is asserted on here — the page half of the merge has its own surface.
            pageDao = mockk<PageDao>(relaxed = true),
            pagesSyncEngine = mockk(relaxed = true),
            purgeRegistry = mockk(relaxed = true),
            reminderDao = mockk(relaxed = true),
            entryCompletionDao = mockk(relaxed = true),
            habitCompletionDao = FakeHabitCompletionDao(),
            checkInDao = FakeCheckInDao(),
            timeLogDao = FakeTimeLogDao(),
            localImages = InMemoryLocalImageStore(),
        )

    private fun entryRecord(uid: String, title: String, updatedAt: Long = 1_000L) = EntrySnapshotRecord(
        uid = uid,
        title = title,
        kind = "TASK",
        status = "PENDING",
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    private fun habitRecord(uid: String, title: String, updatedAt: Long = 1_000L) = HabitSnapshotRecord(
        uid = uid,
        title = title,
        frequency = "1:DAY",
        streak = 0,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    private fun entriesJson(vararg records: EntrySnapshotRecord) = json.encodeToString(records.toList())
    private fun habitsJson(vararg records: HabitSnapshotRecord) = json.encodeToString(records.toList())

    private companion object {
        const val CONFLICT_ENTRIES = "entries_active.sync-conflict-20260901-101500-ABCDEFG.json"
        const val CONFLICT_HABITS = "habits.sync-conflict-20260901-101500-ABCDEFG.json"
        const val PASSPHRASE = "a shared passphrase"
    }

    // ---------------------------------------------------------------- the happy path

    @Test
    fun `a readable conflict file is merged and then deleted`() = runBlocking {
        val entryDao = FakeEntryDao()
        val store = InMemorySyncFileStore()
        store.putRoot(CONFLICT_ENTRIES, entriesJson(entryRecord("uid-1", "From the conflict copy")))

        orchestrator(entryDao, FakeHabitDao()).readAndMerge(store)

        assertEquals(
            "the conflict file's record should have merged into Room",
            listOf("From the conflict copy"),
            entryDao.getAll().map { it.title },
        )
        assertTrue(
            "a fully-merged conflict file carries no further meaning and should be cleaned up",
            store.deletedRootNames.contains(CONFLICT_ENTRIES),
        )
    }

    @Test
    fun `a readable habit conflict file is merged and then deleted`() = runBlocking {
        val habitDao = FakeHabitDao()
        val store = InMemorySyncFileStore()
        store.putRoot(CONFLICT_HABITS, habitsJson(habitRecord("habit-1", "Stretch")))

        orchestrator(FakeEntryDao(), habitDao).readAndMerge(store)

        assertEquals(listOf("Stretch"), habitDao.getAll().map { it.title })
        assertTrue(store.deletedRootNames.contains(CONFLICT_HABITS))
    }

    // ---------------------------------------------------------------- the SYNC-01 cases

    @Test
    fun `an encrypted conflict file is kept when no passphrase is configured`() = runBlocking {
        // Device A has a passphrase set, device B doesn't. B used to destroy every conflict
        // file A produced, unread — the exact scenario that made this a CRITICAL.
        val entryDao = FakeEntryDao()
        val store = InMemorySyncFileStore()
        val key = SnapshotEncryption.deriveKey(PASSPHRASE)
        val payload = SnapshotEncryption.wrapWithMagic(
            SnapshotEncryption.encrypt(entriesJson(entryRecord("uid-1", "Only in the conflict copy")).toByteArray(), key)
        )
        store.putRootBytes(CONFLICT_ENTRIES, payload)

        orchestrator(entryDao, FakeHabitDao()).readAndMerge(store, passphrase = null)

        assertTrue("nothing was readable, so nothing should have merged", entryDao.getAll().isEmpty())
        assertFalse(
            "an unreadable conflict file must survive — it is the only copy of that data",
            store.deletedRootNames.contains(CONFLICT_ENTRIES),
        )
        assertTrue(store.rootNames().contains(CONFLICT_ENTRIES))
    }

    @Test
    fun `an encrypted conflict file is kept when the passphrase is wrong`() = runBlocking {
        val entryDao = FakeEntryDao()
        val store = InMemorySyncFileStore()
        val key = SnapshotEncryption.deriveKey(PASSPHRASE)
        store.putRootBytes(
            CONFLICT_ENTRIES,
            SnapshotEncryption.wrapWithMagic(
                SnapshotEncryption.encrypt(entriesJson(entryRecord("uid-1", "Locked away")).toByteArray(), key)
            ),
        )

        orchestrator(entryDao, FakeHabitDao()).readAndMerge(store, passphrase = "a different passphrase")

        assertTrue(entryDao.getAll().isEmpty())
        assertFalse(store.deletedRootNames.contains(CONFLICT_ENTRIES))
    }

    // ------------------------------------------- the primary files, not just the conflicts

    @Test
    fun `a wrong passphrase on a primary file is reported, so the caller can refuse to write`() = runBlocking {
        // The conflict sweep above already refused to *delete* what it couldn't read. The
        // primary files had no equivalent guard: an undecryptable one merged nothing, and the
        // caller then wrote its own (empty) state back over the folder — re-encrypted under the
        // wrong key, destroying the only copy. §9.4.2 promises a lost passphrase leaves the
        // folder unreadable and recoverable, not overwritten.
        val entryDao = FakeEntryDao()
        val store = InMemorySyncFileStore()
        val key = SnapshotEncryption.deriveKey(PASSPHRASE)
        store.putRootBytes(
            "entries_active.json",
            SnapshotEncryption.wrapWithMagic(
                SnapshotEncryption.encrypt(entriesJson(entryRecord("uid-1", "Locked away")).toByteArray(), key)
            ),
        )

        val result = orchestrator(entryDao, FakeHabitDao()).readAndMerge(store, passphrase = "the wrong one")

        assertTrue(entryDao.getAll().isEmpty())
        assertTrue("the caller must be told, or it will overwrite the folder", result.passphraseMismatch)
        assertEquals(1, result.undecryptableFiles)
    }

    @Test
    fun `a readable folder reports no mismatch`() = runBlocking {
        // The guard must not fire on the ordinary path, or every sync would refuse to write.
        val entryDao = FakeEntryDao()
        val store = InMemorySyncFileStore()
        store.putRoot("entries_active.json", entriesJson(entryRecord("uid-1", "Plaintext is fine")))

        val result = orchestrator(entryDao, FakeHabitDao()).readAndMerge(store, passphrase = null)

        assertFalse(result.passphraseMismatch)
        assertEquals(1, entryDao.getAll().size)
    }

    @Test
    fun `an encrypted conflict file is merged and deleted with the right passphrase`() = runBlocking {
        // The mirror of the two cases above: retention must not become "never delete anything."
        val entryDao = FakeEntryDao()
        val store = InMemorySyncFileStore()
        val key = SnapshotEncryption.deriveKey(PASSPHRASE)
        store.putRootBytes(
            CONFLICT_ENTRIES,
            SnapshotEncryption.wrapWithMagic(
                SnapshotEncryption.encrypt(entriesJson(entryRecord("uid-1", "Recovered")).toByteArray(), key)
            ),
        )

        orchestrator(entryDao, FakeHabitDao()).readAndMerge(store, passphrase = PASSPHRASE)

        assertEquals(listOf("Recovered"), entryDao.getAll().map { it.title })
        assertTrue(store.deletedRootNames.contains(CONFLICT_ENTRIES))
    }

    @Test
    fun `a corrupt conflict file is kept`() = runBlocking {
        // Half-transferred by Syncthing, or written by a build with a different schema.
        val entryDao = FakeEntryDao()
        val store = InMemorySyncFileStore()
        store.putRoot(CONFLICT_ENTRIES, """[{"uid":"uid-1","title":"truncated mid-w""")

        orchestrator(entryDao, FakeHabitDao()).readAndMerge(store)

        assertTrue(entryDao.getAll().isEmpty())
        assertFalse(store.deletedRootNames.contains(CONFLICT_ENTRIES))
    }

    @Test
    fun `a conflict file with an unrecognised name is kept`() = runBlocking {
        // This one fell through the `when` with no else-branch and was deleted anyway.
        val store = InMemorySyncFileStore()
        val unknown = "some_future_domain.sync-conflict-20260901-101500-ABCDEFG.json"
        store.putRoot(unknown, """[{"uid":"x"}]""")

        orchestrator(FakeEntryDao(), FakeHabitDao()).readAndMerge(store)

        assertFalse(
            "an unrecognised conflict file is not ours to delete",
            store.deletedRootNames.contains(unknown),
        )
    }

    @Test
    fun `an unreadable page conflict file is kept`() = runBlocking {
        val store = InMemorySyncFileStore()
        val pageConflict = "abc-123.sync-conflict-20260901-101500-ABCDEFG.json"
        store.putPage(pageConflict, "{ not json at all")

        orchestrator(FakeEntryDao(), FakeHabitDao()).readAndMerge(store)

        assertFalse(store.deletedPageNames.contains(pageConflict))
        assertTrue(store.pageNames().contains(pageConflict))
    }

    @Test
    fun `an empty conflict file is kept rather than treated as merged`() = runBlocking {
        val store = InMemorySyncFileStore()
        store.putRoot(CONFLICT_ENTRIES, "")

        orchestrator(FakeEntryDao(), FakeHabitDao()).readAndMerge(store)

        assertFalse(store.deletedRootNames.contains(CONFLICT_ENTRIES))
    }

    // ---------------------------------------------------------------- surrounding behaviour

    @Test
    fun `ordinary snapshot files are never deleted by the sweep`() = runBlocking {
        val store = InMemorySyncFileStore()
        store.putRoot("entries_active.json", entriesJson(entryRecord("uid-1", "Live task")))
        store.putRoot("habits.json", habitsJson(habitRecord("habit-1", "Stretch")))

        orchestrator(FakeEntryDao(), FakeHabitDao()).readAndMerge(store)

        assertTrue(
            "the sweep only ever touches .sync-conflict- siblings",
            store.deletedRootNames.isEmpty(),
        )
        assertTrue(store.rootNames().containsAll(listOf("entries_active.json", "habits.json")))
    }

    @Test
    fun `merge is last-write-wins on updatedAt and never destroys a newer local record`() = runBlocking {
        val entryDao = FakeEntryDao()
        val store = InMemorySyncFileStore()
        store.putRoot("entries_active.json", entriesJson(entryRecord("uid-1", "Remote name", updatedAt = 1_000L)))
        orchestrator(entryDao, FakeHabitDao()).readAndMerge(store)

        // Local edit, strictly newer than the remote record.
        val local = entryDao.getByUid("uid-1")!!
        entryDao.update(local.copy(title = "Local name", updatedAt = Instant.ofEpochMilli(5_000L)))

        // Same remote file merged again — the older remote copy must not win.
        orchestrator(entryDao, FakeHabitDao()).readAndMerge(store)

        assertEquals(listOf("Local name"), entryDao.getAll().map { it.title })
    }

    @Test
    fun `a conflict file merges alongside the ordinary snapshot without duplicating records`() = runBlocking {
        val entryDao = FakeEntryDao()
        val store = InMemorySyncFileStore()
        store.putRoot("entries_active.json", entriesJson(entryRecord("uid-1", "Original", updatedAt = 1_000L)))
        store.putRoot(CONFLICT_ENTRIES, entriesJson(entryRecord("uid-1", "Conflicted edit", updatedAt = 9_000L)))

        orchestrator(entryDao, FakeHabitDao()).readAndMerge(store)

        // One record, not two: uid is the merge key, and the newer of the two wins.
        assertEquals(1, entryDao.getAll().size)
        assertEquals("Conflicted edit", entryDao.getAll().single().title)
    }
}
