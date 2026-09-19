package com.tendril.app.sync

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.page.PageDao
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import javax.crypto.spec.SecretKeySpec

/**
 * audit 4.1 — an unencrypted file is no longer trusted inside an encrypted folder.
 *
 * `decryptText` returned any file without the `TDRLENC1` prefix as plaintext even when a key was
 * configured, which made AES-GCM's authentication tag worth nothing at the system level: the tag
 * proves a *file* was written by someone holding the key, but nothing forced a file to be
 * encrypted at all. Anyone who could write to the synced folder — another Syncthing peer, a
 * cloud-mirrored copy, anything with the directory mounted — could inject records simply by
 * writing them in the clear.
 *
 * The audit left this unfixed for a stated reason: "reject unencrypted input" and "adopt a
 * folder that was previously plaintext" are the same event, and telling them apart needed 4.2.
 * `sync_meta.json` is what settles it — a folder now says whether it is encrypted, so a
 * plaintext file in one that does is an intruder rather than a folder mid-migration.
 */
class PlaintextTrustTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private class Fixture {
        val entryDao = FakeEntryDao()
        val habitDao = FakeHabitDao()
        val orchestrator = SnapshotSyncOrchestrator(
            entryDao = entryDao,
            habitDao = habitDao,
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
    }

    private fun entryRecord(uid: String, title: String, updatedAt: Long = 1_000L) = EntrySnapshotRecord(
        uid = uid,
        title = title,
        kind = "TASK",
        status = "PENDING",
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    private fun entriesJson(vararg r: EntrySnapshotRecord) = json.encodeToString(r.toList())

    private fun InMemorySyncFileStore.putEncrypted(name: String, content: String, key: SecretKeySpec) =
        putRootBytes(
            name,
            SnapshotEncryption.wrapWithMagic(
                SnapshotEncryption.encrypt(content.toByteArray(Charsets.UTF_8), key)
            ),
        )

    private companion object {
        const val PASSPHRASE = "a shared passphrase"
        const val ENTRIES = "entries_active.json"
        const val CONFLICT_ENTRIES = "entries_active.sync-conflict-20260901-101500-ABCDEFG.json"
    }

    /** A folder this device legitimately owns: encrypted, with its own minted salt and meta. */
    private fun encryptedFolder(): InMemorySyncFileStore {
        val store = InMemorySyncFileStore()
        runBlocking { Fixture().orchestrator.writeSnapshots(store, PASSPHRASE) }
        return store
    }

    // ------------------------------------------------------------ the injection

    @Test
    fun `a plaintext file injected into an encrypted folder is not merged`() = runBlocking {
        val store = encryptedFolder()
        // No key needed to write this: that is the whole point of the attack.
        store.putRoot(ENTRIES, entriesJson(entryRecord("e-evil", "Injected")))

        val f = Fixture()
        f.orchestrator.readAndMerge(store, PASSPHRASE)

        assertNull("an unencrypted record must not be trusted here", f.entryDao.getByUid("e-evil"))
        assertTrue(f.entryDao.getAll().isEmpty())
    }

    @Test
    fun `an injected conflict sibling is refused without being deleted`() = runBlocking {
        val store = encryptedFolder()
        store.putRoot(CONFLICT_ENTRIES, entriesJson(entryRecord("e-evil", "Injected")))

        val f = Fixture()
        f.orchestrator.readAndMerge(store, PASSPHRASE)

        assertNull(f.entryDao.getByUid("e-evil"))
        // Refused reads as "couldn't read this", never "nothing to do" — so the sweep leaves the
        // file for inspection rather than destroying evidence unread.
        assertNotNull("the refused file should still be on disk", store.readRoot(CONFLICT_ENTRIES))
        assertTrue(store.deletedRootNames.none { it == CONFLICT_ENTRIES })
    }

    @Test
    fun `a legacy encrypted folder refuses plaintext too`() = runBlocking {
        // No meta file at all — the folder proves it is encrypted by holding ciphertext.
        val legacyKey = SnapshotEncryption.deriveKey(PASSPHRASE, SnapshotEncryption.LEGACY_SALT)
        val store = InMemorySyncFileStore().apply {
            putEncrypted("habits.json", json.encodeToString(emptyList<HabitSnapshotRecord>()), legacyKey)
            putRoot(ENTRIES, entriesJson(entryRecord("e-evil", "Injected")))
        }

        val f = Fixture()
        f.orchestrator.readAndMerge(store, PASSPHRASE)

        assertNull(f.entryDao.getByUid("e-evil"))
    }

    // ------------------------------------------------------------ no false positives

    @Test
    fun `an unencrypted folder still merges plaintext normally`() = runBlocking {
        // Encryption is off, which is the default (§9.4.2). Every file is legitimately plaintext
        // and refusing here would break sync for everyone not using a passphrase.
        val store = InMemorySyncFileStore().apply {
            putRoot(ENTRIES, entriesJson(entryRecord("e-1", "Buy milk")))
        }

        val f = Fixture()
        f.orchestrator.readAndMerge(store, null)

        assertEquals(listOf("Buy milk"), f.entryDao.getAll().map { it.title })
    }

    @Test
    fun `the encrypted folder's own records still merge`() = runBlocking {
        // The refusal must not swallow the folder's real contents: a device writes, another reads.
        val a = Fixture()
        a.entryDao.insert(
            Entry(
                uid = "e-1",
                title = "Buy milk",
                kind = EntryKind.TASK,
                // Nullable but not defaulted, so they have to be named explicitly.
                startDate = null,
                startTime = null,
                endDate = null,
                endTime = null,
                recurrenceRule = null,
                createdAt = Instant.ofEpochMilli(1_000),
                updatedAt = Instant.ofEpochMilli(1_000),
            )
        )
        val store = InMemorySyncFileStore()
        a.orchestrator.writeSnapshots(store, PASSPHRASE)

        val b = Fixture()
        b.orchestrator.readAndMerge(store, PASSPHRASE)

        assertEquals(listOf("Buy milk"), b.entryDao.getAll().map { it.title })
    }
}
