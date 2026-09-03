package com.tendril.app.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §9.4.2 at-rest snapshot encryption.
 *
 * This class is the reason SYNC-01 was possible: [SnapshotEncryption.decrypt] returns null on a
 * wrong passphrase, the orchestrator turns that into an empty string, and empty was previously
 * indistinguishable from "this file had nothing in it" — which is what got Syncthing conflict
 * files deleted unread. These tests pin the null-on-failure contract that the orchestrator's
 * retention logic now depends on.
 *
 * Key derivation is 210,000 PBKDF2 iterations, so keys are derived once and shared rather than
 * per-test.
 */
class SnapshotEncryptionTest {

    private companion object {
        val KEY = SnapshotEncryption.deriveKey("correct horse battery staple")
        val WRONG_KEY = SnapshotEncryption.deriveKey("correct horse battery stapl")
        const val PLAINTEXT = """[{"uid":"abc","title":"Buy milk"}]"""
    }

    @Test
    fun `round-trips a payload`() {
        val encrypted = SnapshotEncryption.encrypt(PLAINTEXT.toByteArray(), KEY)
        val decrypted = SnapshotEncryption.decrypt(encrypted, KEY)
        assertEquals(PLAINTEXT, decrypted?.toString(Charsets.UTF_8))
    }

    @Test
    fun `same plaintext encrypts differently each time`() {
        // A fresh random IV per call — identical ciphertext across two encryptions of the same
        // snapshot would leak that nothing changed between syncs.
        val first = SnapshotEncryption.encrypt(PLAINTEXT.toByteArray(), KEY)
        val second = SnapshotEncryption.encrypt(PLAINTEXT.toByteArray(), KEY)
        assertFalse(first.contentEquals(second))
        assertArrayEquals(
            SnapshotEncryption.decrypt(first, KEY),
            SnapshotEncryption.decrypt(second, KEY),
        )
    }

    @Test
    fun `decrypt returns null for the wrong passphrase rather than throwing`() {
        val encrypted = SnapshotEncryption.encrypt(PLAINTEXT.toByteArray(), KEY)
        assertNull(SnapshotEncryption.decrypt(encrypted, WRONG_KEY))
    }

    @Test
    fun `decrypt returns null for truncated ciphertext`() {
        // A file half-written by an interrupted sync, or half-transferred by Syncthing.
        val encrypted = SnapshotEncryption.encrypt(PLAINTEXT.toByteArray(), KEY)
        assertNull(SnapshotEncryption.decrypt(encrypted.copyOfRange(0, encrypted.size / 2), KEY))
    }

    @Test
    fun `decrypt returns null for input shorter than the IV`() {
        assertNull(SnapshotEncryption.decrypt(ByteArray(4), KEY))
    }

    @Test
    fun `deriveKey is deterministic for the same passphrase`() {
        // Every device sharing the folder must land on the same key from the same passphrase —
        // that's why the salt is fixed rather than random per install.
        assertArrayEquals(
            SnapshotEncryption.deriveKey("shared secret").encoded,
            SnapshotEncryption.deriveKey("shared secret").encoded,
        )
    }

    @Test
    fun `deriveKey does not trim whitespace`() {
        // SecretStore deliberately skips trim() for the passphrase; a key derived from a
        // trimmed variant must not match, or a stray space would silently still decrypt.
        assertFalse(
            SnapshotEncryption.deriveKey(" padded ").encoded
                .contentEquals(SnapshotEncryption.deriveKey("padded").encoded)
        )
    }

    @Test
    fun `magic marker distinguishes encrypted payloads from plain JSON`() {
        val wrapped = SnapshotEncryption.wrapWithMagic(SnapshotEncryption.encrypt(PLAINTEXT.toByteArray(), KEY))
        assertTrue(SnapshotEncryption.isEncrypted(wrapped))
        assertEquals(
            PLAINTEXT,
            SnapshotEncryption.decrypt(SnapshotEncryption.stripMagic(wrapped), KEY)?.toString(Charsets.UTF_8),
        )
    }

    @Test
    fun `plain JSON is not mistaken for an encrypted payload`() {
        assertFalse(SnapshotEncryption.isEncrypted(PLAINTEXT.toByteArray()))
        assertFalse(SnapshotEncryption.isEncrypted(ByteArray(0)))
        assertFalse(SnapshotEncryption.isEncrypted("TDRL".toByteArray())) // shorter than the marker
    }
}
