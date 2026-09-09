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
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

/**
 * §9.4.2 / audit 4.2 — the PBKDF2 salt travels in `sync_meta.json` instead of being a
 * compile-time constant.
 *
 * The old reasoning was that a random salt needs a channel to exchange it over and Tendril has
 * none, having no account system. The premise was wrong: the sync folder itself is the channel.
 * With a fixed salt, one precomputed table works against every install on earth and two people
 * who pick the same passphrase get byte-identical keys. With a per-folder random salt, neither
 * holds.
 *
 * Two things have to stay true while that changes: a folder written before this must keep
 * opening (it has no meta file, so it is on [SnapshotEncryption.LEGACY_SALT]), and two devices
 * that mint a salt independently before ever syncing must converge on the same one.
 */
class SyncMetaSaltTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun orchestrator() = SnapshotSyncOrchestrator(
        entryDao = FakeEntryDao(),
        habitDao = FakeHabitDao(),
        pageDao = mockk<PageDao>(relaxed = true),
        pagesSyncEngine = mockk(relaxed = true),
        purgeRegistry = mockk(relaxed = true),
        reminderDao = mockk(relaxed = true),
        entryCompletionDao = mockk(relaxed = true),
        localImages = InMemoryLocalImageStore(),
    )

    private fun InMemorySyncFileStore.putEncrypted(name: String, content: String, key: SecretKeySpec) =
        putRootBytes(
            name,
            SnapshotEncryption.wrapWithMagic(
                SnapshotEncryption.encrypt(content.toByteArray(Charsets.UTF_8), key)
            ),
        )

    private fun InMemorySyncFileStore.bytes(name: String): ByteArray? = runBlocking { readRoot(name) }

    private fun InMemorySyncFileStore.meta(): SyncMetaRecord? =
        bytes(META)?.let { json.decodeFromString<SyncMetaRecord>(it.toString(Charsets.UTF_8)) }

    private fun saltOf(store: InMemorySyncFileStore): ByteArray =
        Base64.getDecoder().decode(requireNotNull(store.meta()) { "no meta file" }.salt)

    private fun metaJson(salt: ByteArray, createdAt: Long) =
        json.encodeToString(SyncMetaRecord(salt = Base64.getEncoder().encodeToString(salt), createdAt = createdAt))

    private companion object {
        const val PASSPHRASE = "a shared passphrase"
        const val META = "sync_meta.json"
        const val ENTRIES = "entries_active.json"
        const val CONFLICT_META = "sync_meta.sync-conflict-20260901-101500-ABCDEFG.json"
    }

    // ------------------------------------------------------------ minting

    @Test
    fun `a new encrypted folder gets a meta file holding a random salt`() = runBlocking {
        val store = InMemorySyncFileStore()

        orchestrator().writeSnapshots(store, PASSPHRASE)

        val meta = store.meta()
        assertNotNull("an encrypted write should mint the folder's meta file", meta)
        assertEquals(SnapshotEncryption.SALT_LENGTH_BYTES, saltOf(store).size)
        assertFalse(
            "the minted salt must not be the compile-time one",
            saltOf(store).contentEquals(SnapshotEncryption.LEGACY_SALT),
        )
        // The meta file is the one thing that must stay readable without the key.
        assertFalse(SnapshotEncryption.isEncrypted(store.bytes(META)!!))
    }

    @Test
    fun `an unencrypted folder gets no meta file`() = runBlocking {
        val store = InMemorySyncFileStore()
        orchestrator().writeSnapshots(store, null)
        assertNull("no passphrase means no encryption and nothing to mark", store.bytes(META))
    }

    @Test
    fun `reading never mints a meta file`() = runBlocking {
        val store = InMemorySyncFileStore()
        orchestrator().readAndMerge(store, PASSPHRASE)
        assertNull("a read must not create files in the folder", store.bytes(META))
    }

    // ------------------------------------- the actual 4.2 fix: no shared key across folders

    @Test
    fun `the same passphrase gives different keys in two different folders`() = runBlocking {
        val a = InMemorySyncFileStore()
        val b = InMemorySyncFileStore()
        orchestrator().writeSnapshots(a, PASSPHRASE)
        orchestrator().writeSnapshots(b, PASSPHRASE)

        assertFalse("two folders must not share a salt", saltOf(a).contentEquals(saltOf(b)))

        // The consequence that matters: a key derived for one folder opens nothing in the other,
        // so one precomputed table no longer works everywhere.
        val keyForB = SnapshotEncryption.deriveKey(PASSPHRASE, saltOf(b))
        assertNull(
            "folder A's snapshot should not open under folder B's key",
            SnapshotEncryption.decrypt(SnapshotEncryption.stripMagic(a.bytes(ENTRIES)!!), keyForB),
        )
    }

    @Test
    fun `a folder still round-trips under its own salt`() = runBlocking {
        val store = InMemorySyncFileStore()
        orchestrator().writeSnapshots(store, PASSPHRASE)

        val key = SnapshotEncryption.deriveKey(PASSPHRASE, saltOf(store))
        assertNotNull(
            "the folder must open under the passphrase it was written with",
            SnapshotEncryption.decrypt(SnapshotEncryption.stripMagic(store.bytes(ENTRIES)!!), key),
        )
    }

    // ------------------------------------------------------------ legacy folders

    @Test
    fun `a folder written before sync_meta keeps opening, and gains the marker`() = runBlocking {
        // Exactly what the old build left behind: ciphertext under the compile-time salt, no meta.
        val legacyKey = SnapshotEncryption.deriveKey(PASSPHRASE, SnapshotEncryption.LEGACY_SALT)
        val store = InMemorySyncFileStore().apply {
            putEncrypted(ENTRIES, json.encodeToString(emptyList<EntrySnapshotRecord>()), legacyKey)
        }

        // Must not be treated as a wrong passphrase -- if resolveSalt minted a random salt here,
        // the write guard would refuse and the person would be locked out of their own folder.
        orchestrator().writeSnapshots(store, PASSPHRASE)

        assertArrayEquals(
            "a legacy folder must stay on the salt it was written with",
            SnapshotEncryption.LEGACY_SALT,
            saltOf(store),
        )
        assertNotNull(
            "and must still open under the same passphrase",
            SnapshotEncryption.decrypt(SnapshotEncryption.stripMagic(store.bytes(ENTRIES)!!), legacyKey),
        )
    }

    // ------------------------------------------------------------ the bootstrap race

    @Test
    fun `the earliest meta wins across a conflict sibling`() = runBlocking {
        val early = ByteArray(16) { 1 }
        val late = ByteArray(16) { 2 }
        // Whichever file Syncthing happened to call the "real" one, the earlier salt is adopted.
        val store = InMemorySyncFileStore().apply {
            putRoot(META, metaJson(late, createdAt = 2_000))
            putRoot(CONFLICT_META, metaJson(early, createdAt = 1_000))
        }

        orchestrator().writeSnapshots(store, PASSPHRASE)

        assertArrayEquals("the earlier salt should have won", early, saltOf(store))
        assertNull("the adopted sibling should be cleared", store.bytes(CONFLICT_META))
        assertNotNull(
            "and the folder should be written under the winning salt",
            SnapshotEncryption.decrypt(
                SnapshotEncryption.stripMagic(store.bytes(ENTRIES)!!),
                SnapshotEncryption.deriveKey(PASSPHRASE, early),
            ),
        )
    }

    @Test
    fun `losing the salt race refuses the write rather than destroying the snapshots`() = runBlocking {
        // This device already published under its own salt...
        val store = InMemorySyncFileStore()
        orchestrator().writeSnapshots(store, PASSPHRASE)
        val mine = saltOf(store)
        val before = store.bytes(ENTRIES)!!

        // ...and then an earlier-minted salt arrives from the other device and wins.
        val theirs = ByteArray(16) { 9 }
        store.putRoot(CONFLICT_META, metaJson(theirs, createdAt = 1L))

        // The key now derives from their salt, which opens none of my ciphertext. The write guard
        // is what stands between a lost race and a destroyed folder.
        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking { orchestrator().writeSnapshots(store, PASSPHRASE) }
        }
        assertTrue(
            "should report the passphrase not opening the folder: ${failure.message}",
            failure.message!!.contains("does not open them"),
        )
        assertArrayEquals("the snapshots must survive a lost race", before, store.bytes(ENTRIES))
        // persistSalt runs only after the guard passes, so a refused write must not have claimed
        // the folder for the winning salt either -- nothing at all should have moved.
        assertArrayEquals("the meta file should be untouched by a refused write", mine, saltOf(store))
    }
}
