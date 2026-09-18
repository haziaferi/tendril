package com.tendril.app.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** §0.10 item 20 — the key file's one line around a wrap: bare, wrapped, unopenable, and the rewrap rule. */
class SecretFileFormatTest {
    /** A stand-in for DPAPI: reverses the bytes, refuses a blob it did not make. */
    private object Reverse : SecretWrap {
        override fun protect(plain: ByteArray): ByteArray = byteArrayOf(0x7F) + plain.reversedArray()
        override fun unprotect(stored: ByteArray): ByteArray {
            require(stored.firstOrNull() == 0x7F.toByte()) { "not ours" }
            return stored.drop(1).toByteArray().reversedArray()
        }
    }
    private object Broken : SecretWrap {
        override fun protect(plain: ByteArray): ByteArray = error("no DPAPI here")
        override fun unprotect(stored: ByteArray): ByteArray = error("no DPAPI here")
    }

    @Test
    fun `a wrapped line round-trips and never holds the key in the clear`() {
        val line = SecretFileFormat.encode("sk-ant-secret", Reverse)
        assertTrue(line.startsWith("dpapi:"))
        assertFalse(line.contains("secret"))
        assertEquals("sk-ant-secret", SecretFileFormat.decode(line, Reverse))
    }

    @Test
    fun `no wrap writes the bare key and reads it back`() {
        assertEquals("sk-ant-secret", SecretFileFormat.encode("sk-ant-secret", SecretWrap.None))
        assertEquals("sk-ant-secret", SecretFileFormat.decode("sk-ant-secret\n", SecretWrap.None))
    }

    @Test
    fun `a bare line from before is read under a wrap and asks to be rewrapped`() {
        assertEquals("sk-ant-secret", SecretFileFormat.decode("sk-ant-secret", Reverse))
        assertTrue(SecretFileFormat.wantsRewrap("sk-ant-secret", Reverse))
        assertFalse(SecretFileFormat.wantsRewrap(SecretFileFormat.encode("sk-ant-secret", Reverse), Reverse))
        assertFalse(SecretFileFormat.wantsRewrap("sk-ant-secret", SecretWrap.None))
    }

    @Test
    fun `a blob this wrap cannot open is no key, not a wrong one`() {
        val other = SecretFileFormat.encode("sk-ant-secret", Reverse)
        assertNull(SecretFileFormat.decode(other, object : SecretWrap { override fun protect(plain: ByteArray) = plain; override fun unprotect(stored: ByteArray) = error("another account") }))
        assertNull(SecretFileFormat.decode(other, SecretWrap.None))
        assertNull(SecretFileFormat.decode("dpapi:not-base64!!", Reverse))
        assertNull(SecretFileFormat.decode("   ", Reverse))
    }

    @Test
    fun `a wrap that fails to protect falls back to the bare line`() {
        assertEquals("sk-ant-secret", SecretFileFormat.encode("sk-ant-secret", Broken))
    }
}
