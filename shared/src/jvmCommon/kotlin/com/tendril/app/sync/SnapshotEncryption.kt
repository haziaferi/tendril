package com.tendril.app.sync

import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * §9.4.2 — optional at-rest snapshot encryption, off by default. AES-256-GCM with a
 * passphrase-derived key (PBKDF2WithHmacSHA256 — spec leaves the exact KDF unspecified;
 * PBKDF2 is chosen over Argon2id here since it's built into the JDK with no extra
 * dependency, and this isn't a high-value password-cracking target — a personal sync
 * folder, not a server-side credential store).
 *
 * The salt used to be a fixed, app-specific constant, on the reasoning that every device
 * sharing the folder must derive the *same* key from the *same* passphrase with no channel to
 * exchange a random salt over (Tendril has no account or identity system, §9.4.2). The premise
 * was wrong: there *is* a channel, and it is the sync folder itself. A random salt now travels
 * in `sync_meta.json` beside the snapshots, so one precomputed table no longer works against
 * every Tendril install and two people who choose the same passphrase no longer get the same
 * key. [LEGACY_SALT] stays for folders written before that, which must keep opening.
 *
 * §12.5/Milestone 2 — moved into `shared/jvmCommon` (Android and desktop both being JVM
 * targets) so it's written once, not duplicated per platform.
 */
object SnapshotEncryption {
    private const val ITERATIONS = 210_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    const val SALT_LENGTH_BYTES = 16

    /** The compile-time salt every folder used before `sync_meta.json` existed. A folder with
     * encrypted snapshots and no meta file was written under this one, and re-deriving its key
     * under a random salt would make it unreadable — so this is a compatibility constant, not a
     * default for anything new. */
    val LEGACY_SALT: ByteArray = "tendril-snapshot-encryption-v1".toByteArray(Charsets.UTF_8)

    fun randomSalt(): ByteArray = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }

    fun deriveKey(passphrase: String, salt: ByteArray = LEGACY_SALT): SecretKeySpec {
        val spec: KeySpec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }

    fun encrypt(plaintext: ByteArray, key: SecretKeySpec): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext // IV travels with the file — it isn't secret, only the key is
    }

    /** Returns null on failure (wrong passphrase, or the file isn't actually encrypted) —
     * callers treat that as "couldn't read this snapshot," not a crash. */
    fun decrypt(payload: ByteArray, key: SecretKeySpec): ByteArray? = runCatching {
        val iv = payload.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = payload.copyOfRange(GCM_IV_LENGTH_BYTES, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }
        cipher.doFinal(ciphertext)
    }.getOrNull()

    /** A cheap marker prefix so a reader can tell an encrypted file from a plain JSON one
     * without attempting (and possibly mis-attempting) decryption on ordinary content. */
    private val MAGIC = "TDRLENC1".toByteArray(Charsets.UTF_8)

    fun wrapWithMagic(encrypted: ByteArray): ByteArray = MAGIC + encrypted
    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size >= MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)
    fun stripMagic(bytes: ByteArray): ByteArray = bytes.copyOfRange(MAGIC.size, bytes.size)
}
