package com.tendril.app.sync

import com.tendril.app.data.page.PageDao
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

/**
 * §9.4.2 — re-keying a sync folder, and the precondition that makes it safe.
 *
 * Re-keying is decrypt-then-encrypt, so it can only preserve what the re-keying device can
 * actually read. Anything in the folder it could not decrypt would be replaced by whatever that
 * device happens to hold. So the operation merges under the current passphrase first and refuses
 * outright if anything failed — a device that cannot read the folder has no business rewriting
 * it, however deliberate the person was.
 *
 * The guard this reaches past ([SnapshotSyncOrchestrator.writeSnapshots]'s `allowRekey`) is
 * unreachable from anywhere else, which is the point: it is not a flag callers may set, it is
 * the far side of this check.
 */
class RekeyTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private class Fixture {
        val entryDao = FakeEntryDao()
        val orchestrator = SnapshotSyncOrchestrator(
            entryDao = entryDao,
            habitDao = FakeHabitDao(),
            pageDao = mockk<PageDao>(relaxed = true),
            pagesSyncEngine = mockk(relaxed = true),
            purgeRegistry = mockk(relaxed = true),
            reminderDao = mockk(relaxed = true),
            entryCompletionDao = mockk(relaxed = true),
            habitCompletionDao = FakeHabitCompletionDao(),
            localImages = InMemoryLocalImageStore(),
        )
    }

    private fun entryRecord(uid: String, title: String) = EntrySnapshotRecord(
        uid = uid,
        title = title,
        kind = "TASK",
        status = "PENDING",
        createdAt = 1_000,
        updatedAt = 1_000,
    )

    private fun InMemorySyncFileStore.bytes(name: String) = runBlocking { readRoot(name) }

    private fun InMemorySyncFileStore.putEncrypted(name: String, content: String, key: SecretKeySpec) =
        putRootBytes(
            name,
            SnapshotEncryption.wrapWithMagic(
                SnapshotEncryption.encrypt(content.toByteArray(Charsets.UTF_8), key)
            ),
        )

    private fun saltOf(store: InMemorySyncFileStore): ByteArray =
        java.util.Base64.getDecoder().decode(
            json.decodeFromString(SyncMetaRecord.serializer(), store.bytes(META)!!.toString(Charsets.UTF_8)).salt
        )

    private companion object {
        const val OLD = "the old passphrase"
        const val NEW = "a completely new passphrase"
        const val META = "sync_meta.json"
        const val ENTRIES = "entries_active.json"
    }

    /** A folder this device owns outright: written by it, under [OLD]. */
    private fun ownedFolder(): Pair<Fixture, InMemorySyncFileStore> {
        val f = Fixture()
        val store = InMemorySyncFileStore()
        runBlocking { f.orchestrator.writeSnapshots(store, OLD) }
        return f to store
    }

    // ------------------------------------------------------------ the happy path

    @Test
    fun `re-keying re-encrypts the folder under the new passphrase`() = runBlocking {
        val (f, store) = ownedFolder()

        val outcome = f.orchestrator.rekey(store, currentPassphrase = OLD, newPassphrase = NEW)

        assertEquals(RekeyOutcome.Completed, outcome)
        val key = SnapshotEncryption.deriveKey(NEW, saltOf(store))
        assertNotNull(
            "the folder should now open under the new passphrase",
            SnapshotEncryption.decrypt(SnapshotEncryption.stripMagic(store.bytes(ENTRIES)!!), key),
        )
    }

    @Test
    fun `the old passphrase no longer opens the folder afterwards`() = runBlocking {
        val (f, store) = ownedFolder()
        val oldKey = SnapshotEncryption.deriveKey(OLD, saltOf(store))

        f.orchestrator.rekey(store, currentPassphrase = OLD, newPassphrase = NEW)

        assertNull(
            "a re-key that left the old key working would not be a re-key",
            SnapshotEncryption.decrypt(SnapshotEncryption.stripMagic(store.bytes(ENTRIES)!!), oldKey),
        )
    }

    @Test
    fun `the folder's salt is unchanged, so its identity survives`() = runBlocking {
        val (f, store) = ownedFolder()
        val saltBefore = saltOf(store)

        f.orchestrator.rekey(store, currentPassphrase = OLD, newPassphrase = NEW)

        // The salt is not a secret and re-keying is not a new folder — minting a fresh one would
        // make every other device treat this as a folder it had never seen.
        assertArrayEquals(saltBefore, saltOf(store))
    }

    @Test
    fun `content survives the re-key`() = runBlocking {
        val f = Fixture()
        val store = InMemorySyncFileStore()
        // A record only the folder holds. It has to be merged in before the encrypted write,
        // because a write publishes what the *database* holds -- writing first would overwrite
        // the record with this fixture's empty dao, which is what the first version of this
        // test did.
        store.putRoot(ENTRIES, json.encodeToString(listOf(entryRecord("e-1", "Buy milk"))))
        f.orchestrator.readAndMerge(store, null)
        assertEquals("precondition: the record is in the database", 1, f.entryDao.getAll().size)
        f.orchestrator.writeSnapshots(store, OLD)

        f.orchestrator.rekey(store, currentPassphrase = OLD, newPassphrase = NEW)

        val reader = Fixture()
        reader.orchestrator.readAndMerge(store, NEW)
        assertEquals(listOf("Buy milk"), reader.entryDao.getAll().map { it.title })
    }

    // ------------------------------------------------------- the precondition doing its job

    @Test
    fun `re-keying is refused when the current passphrase cannot read the folder`() = runBlocking {
        val (_, store) = ownedFolder()
        val before = store.bytes(ENTRIES)!!

        // A second device that never held the folder's passphrase tries to re-key it.
        val stranger = Fixture()
        val outcome = stranger.orchestrator.rekey(
            store,
            currentPassphrase = "not the folder's passphrase",
            newPassphrase = NEW,
        )

        assertTrue("must refuse rather than rewrite", outcome is RekeyOutcome.RefusedUnreadable)
        assertTrue((outcome as RekeyOutcome.RefusedUnreadable).undecryptableFiles > 0)
        assertArrayEquals("nothing may have been written", before, store.bytes(ENTRIES))
    }

    @Test
    fun `a refused re-key leaves the folder openable by the passphrase it already had`() = runBlocking {
        val (_, store) = ownedFolder()
        val salt = saltOf(store)

        Fixture().orchestrator.rekey(store, currentPassphrase = "wrong", newPassphrase = NEW)

        assertNotNull(
            "the folder must be exactly as it was",
            SnapshotEncryption.decrypt(
                SnapshotEncryption.stripMagic(store.bytes(ENTRIES)!!),
                SnapshotEncryption.deriveKey(OLD, salt),
            ),
        )
    }

    @Test
    fun `re-keying a folder written under the legacy salt is refused unless it can be read`() = runBlocking {
        // A folder from before sync_meta.json existed: ciphertext under the compile-time salt.
        val legacyKey = SnapshotEncryption.deriveKey(OLD, SnapshotEncryption.LEGACY_SALT)
        val store = InMemorySyncFileStore().apply {
            putEncrypted(ENTRIES, json.encodeToString(emptyList<EntrySnapshotRecord>()), legacyKey)
        }

        val outcome = Fixture().orchestrator.rekey(store, currentPassphrase = "wrong", newPassphrase = NEW)

        assertTrue(outcome is RekeyOutcome.RefusedUnreadable)
    }

    @Test
    fun `a legacy folder can be re-keyed when the passphrase does read it`() = runBlocking {
        val legacyKey = SnapshotEncryption.deriveKey(OLD, SnapshotEncryption.LEGACY_SALT)
        val store = InMemorySyncFileStore().apply {
            putEncrypted(ENTRIES, json.encodeToString(emptyList<EntrySnapshotRecord>()), legacyKey)
        }

        val outcome = Fixture().orchestrator.rekey(store, currentPassphrase = OLD, newPassphrase = NEW)

        assertEquals(RekeyOutcome.Completed, outcome)
        // It keeps the legacy salt — the folder predates sync_meta.json and re-keying changes
        // the key, not which folder this is.
        assertArrayEquals(SnapshotEncryption.LEGACY_SALT, saltOf(store))
    }

    // ------------------------------------------------------------ an unencrypted folder

    @Test
    fun `a plain folder can be encrypted by re-keying into it`() = runBlocking {
        val f = Fixture()
        val store = InMemorySyncFileStore().apply {
            putRoot(ENTRIES, json.encodeToString(listOf(entryRecord("e-1", "Buy milk"))))
        }

        // Nothing is encrypted, so nothing fails to decrypt and the precondition is satisfied.
        val outcome = f.orchestrator.rekey(store, currentPassphrase = null, newPassphrase = NEW)

        assertEquals(RekeyOutcome.Completed, outcome)
        assertTrue(SnapshotEncryption.isEncrypted(store.bytes(ENTRIES)!!))
    }
}
