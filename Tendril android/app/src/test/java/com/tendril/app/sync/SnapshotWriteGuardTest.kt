package com.tendril.app.sync

import com.tendril.app.data.page.PageDao
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

/**
 * The write half of sync is a full overwrite, so anything it cannot read first, it destroys.
 *
 * [SnapshotSyncOrchestrator.writeSnapshots] already refused to publish cleartext over an
 * encrypted folder, but it decided that on `key == null` alone. A *mistyped* passphrase derives
 * a perfectly good key, so it passed that check, read nothing (an undecryptable file becomes
 * `""`, indistinguishable from an empty one), and then re-encrypted every snapshot under a key
 * nobody knows — strictly worse than the cleartext downgrade the guard was written to stop,
 * because cleartext is at least still readable.
 *
 * The rule these tests pin: **never overwrite an encrypted folder this key cannot open**, with a
 * deliberate re-key as the one opt-in exception.
 */
class SnapshotWriteGuardTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun orchestrator(entryDao: FakeEntryDao, habitDao: FakeHabitDao) =
        SnapshotSyncOrchestrator(
            entryDao = entryDao,
            habitDao = habitDao,
            // The page half has its own surface (PageMergeTest); nothing here asserts on it.
            pageDao = mockk<PageDao>(relaxed = true),
            pagesSyncEngine = mockk(relaxed = true),
            purgeRegistry = mockk(relaxed = true),
            reminderDao = mockk(relaxed = true),
            entryCompletionDao = mockk(relaxed = true),
        )

    private fun entryRecord(uid: String, title: String, updatedAt: Long = 1_000L) = EntrySnapshotRecord(
        uid = uid,
        title = title,
        kind = "TASK",
        status = "PENDING",
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    /** Writes [content] into the store the way an encrypted snapshot actually lands on disk —
     * magic prefix, then AES-GCM under [key] — without going through the orchestrator, so the
     * folder's starting state is set up independently of the code under test. */
    private fun InMemorySyncFileStore.putEncrypted(name: String, content: String, key: SecretKeySpec) =
        putRootBytes(
            name,
            SnapshotEncryption.wrapWithMagic(
                SnapshotEncryption.encrypt(content.toByteArray(Charsets.UTF_8), key)
            ),
        )

    /** An encrypted folder holding one entry, as another device would have left it. */
    private fun encryptedFolder(): InMemorySyncFileStore {
        val key = SnapshotEncryption.deriveKey(RIGHT)
        return InMemorySyncFileStore().apply {
            putEncrypted(ENTRIES_ACTIVE, json.encodeToString(listOf(entryRecord("e-1", "Buy milk"))), key)
            putEncrypted(HABITS, json.encodeToString(emptyList<HabitSnapshotRecord>()), key)
        }
    }

    private fun InMemorySyncFileStore.snapshotOf(name: String): ByteArray =
        requireNotNull(runBlocking { readRoot(name) }) { "$name missing" }

    private companion object {
        const val RIGHT = "the actual passphrase"
        const val WRONG = "the actual passphras" // one character short — an ordinary typo
        const val ENTRIES_ACTIVE = "entries_active.json"
        const val HABITS = "habits.json"
    }

    // ------------------------------------------------------------ the regression

    @Test
    fun `a mistyped passphrase refuses the write and leaves every snapshot byte-identical`() = runBlocking {
        val store = encryptedFolder()
        val before = store.rootNames().associateWith { store.snapshotOf(it) }

        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking { orchestrator(FakeEntryDao(), FakeHabitDao()).writeSnapshots(store, WRONG) }
        }

        assertTrue(
            "the message should name the passphrase as the problem, not the absence of one: ${failure.message}",
            failure.message!!.contains("does not open them"),
        )
        assertEquals("no file should have been added or removed", before.keys, store.rootNames())
        before.forEach { (name, bytes) ->
            assertArrayEquals("$name was rewritten despite the refusal", bytes, store.snapshotOf(name))
        }
    }

    @Test
    fun `the data survives a mistyped passphrase and still opens under the right one`() = runBlocking {
        val store = encryptedFolder()

        // syncNow is the button: it merges (reading nothing, since the key is wrong) and then
        // writes. The write is what has to stop.
        assertThrows(IllegalStateException::class.java) {
            runBlocking { orchestrator(FakeEntryDao(), FakeHabitDao()).syncNow(store, WRONG) }
        }

        // A device that knows the real passphrase can still read the folder afterwards.
        val recovered = FakeEntryDao()
        orchestrator(recovered, FakeHabitDao()).readAndMerge(store, RIGHT)
        assertEquals(listOf("Buy milk"), recovered.getAll().map { it.title })
    }

    // ------------------------------------------------- the guard's original case, unchanged

    @Test
    fun `no passphrase against an encrypted folder is still refused`() = runBlocking {
        val store = encryptedFolder()
        val before = store.snapshotOf(ENTRIES_ACTIVE)

        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking { orchestrator(FakeEntryDao(), FakeHabitDao()).writeSnapshots(store, null) }
        }

        assertTrue(
            "the no-passphrase case should keep its own wording: ${failure.message}",
            failure.message!!.contains("no passphrase was given"),
        )
        assertArrayEquals(before, store.snapshotOf(ENTRIES_ACTIVE))
    }

    @Test
    fun `allowRekey does not license a downgrade to cleartext`() = runBlocking {
        val store = encryptedFolder()
        val before = store.snapshotOf(ENTRIES_ACTIVE)

        // Re-keying is about swapping one key for another. Turning encryption *off* over a folder
        // that has it on is the original hazard, and the opt-in must not reopen it.
        assertThrows(IllegalStateException::class.java) {
            runBlocking { orchestrator(FakeEntryDao(), FakeHabitDao()).writeSnapshots(store, null, allowRekey = true) }
        }
        assertArrayEquals(before, store.snapshotOf(ENTRIES_ACTIVE))
    }

    // ------------------------------------------------------------ no false positives

    @Test
    fun `the right passphrase writes normally`() = runBlocking {
        val store = encryptedFolder()

        orchestrator(FakeEntryDao(), FakeHabitDao()).writeSnapshots(store, RIGHT)

        val written = store.snapshotOf(ENTRIES_ACTIVE)
        assertTrue("the folder should still be encrypted", SnapshotEncryption.isEncrypted(written))
        assertNotNull(
            "and should open under the passphrase it was written with",
            SnapshotEncryption.decrypt(SnapshotEncryption.stripMagic(written), SnapshotEncryption.deriveKey(RIGHT)),
        )
    }

    @Test
    fun `a plain-JSON folder is untouched by the guard`() = runBlocking {
        val store = InMemorySyncFileStore().apply {
            putRoot(ENTRIES_ACTIVE, json.encodeToString(listOf(entryRecord("e-1", "Buy milk"))))
        }

        // Nothing here is encrypted, so there is nothing a missing key could destroy — an
        // unencrypted folder must keep syncing without a passphrase.
        orchestrator(FakeEntryDao(), FakeHabitDao()).writeSnapshots(store, null)

        assertFalse(SnapshotEncryption.isEncrypted(store.snapshotOf(ENTRIES_ACTIVE)))
    }

    @Test
    fun `a deliberate re-key is allowed through`() = runBlocking {
        val store = encryptedFolder()
        val newPassphrase = "a completely different passphrase"

        orchestrator(FakeEntryDao(), FakeHabitDao()).writeSnapshots(store, newPassphrase, allowRekey = true)

        val written = store.snapshotOf(ENTRIES_ACTIVE)
        assertNotNull(
            "the folder should now open under the new passphrase",
            SnapshotEncryption.decrypt(SnapshotEncryption.stripMagic(written), SnapshotEncryption.deriveKey(newPassphrase)),
        )
    }
}
