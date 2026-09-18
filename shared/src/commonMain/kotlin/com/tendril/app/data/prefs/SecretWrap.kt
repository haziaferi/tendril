package com.tendril.app.data.prefs

import java.util.Base64

/**
 * §0.10 item 20 (2026-09-18) — how a secret is written to a file the OS can protect. The desktop's
 * key file is one line: either the bare key (the form since §0.6.15, still read) or
 * `dpapi:` + base64 of the bytes a [SecretWrap] returned. The wrap is the platform's — Windows'
 * DPAPI through `jna-platform` in the desktop module (`DpapiWrap`); [SecretWrap.None] where there
 * is no such thing, which leaves the file bare under the owner-only permissions it had. A bare
 * file is rewritten wrapped the first time it is read with a wrap that works, so a key saved
 * before this lands is not left in the clear.
 */
interface SecretWrap {
    /** The bytes to store for [plain]; a failure means "store it bare" (the caller decides). */
    fun protect(plain: ByteArray): ByteArray
    /** The bytes back, or throws — a blob from another user or machine cannot be opened, and the
     * store treats that as no key rather than a key that is wrong. */
    fun unprotect(stored: ByteArray): ByteArray

    object None : SecretWrap {
        override fun protect(plain: ByteArray): ByteArray = plain
        override fun unprotect(stored: ByteArray): ByteArray = stored
    }
}

/** The one-line file format around a [SecretWrap]; pure, so the Android test suite proves it. */
object SecretFileFormat {
    const val WRAPPED_PREFIX = "dpapi:"

    /** The line to write for [key]: wrapped when the wrap succeeds, bare when it throws. */
    fun encode(key: String, wrap: SecretWrap): String {
        val blob = runCatching { wrap.protect(key.toByteArray(Charsets.UTF_8)) }.getOrNull()
        return if (blob == null || wrap === SecretWrap.None) key else WRAPPED_PREFIX + Base64.getEncoder().encodeToString(blob)
    }

    /** The key in [line], or null when the line is blank or a wrapped blob this wrap cannot open. */
    fun decode(line: String, wrap: SecretWrap): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        if (!trimmed.startsWith(WRAPPED_PREFIX)) return trimmed
        if (wrap === SecretWrap.None) return null // a blob written under DPAPI, read where there is none
        return runCatching {
            val blob = Base64.getDecoder().decode(trimmed.removePrefix(WRAPPED_PREFIX))
            String(wrap.unprotect(blob), Charsets.UTF_8).trim().takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    /** True when [line] is a bare key that [wrap] could now protect — the store rewrites it. */
    fun wantsRewrap(line: String, wrap: SecretWrap): Boolean =
        wrap !== SecretWrap.None && line.trim().isNotEmpty() && !line.trim().startsWith(WRAPPED_PREFIX)
}
