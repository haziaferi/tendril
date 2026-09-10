package com.tendril.app.sync

/**
 * §9.4.2 — the wrapper that lets an image's *name* be as protected as its bytes.
 *
 * An encrypted folder holds `images/<block uid>.tdrlimg`, and everything about the picture except
 * which block owns it lives inside the ciphertext: the original file name, and therefore the file
 * type, along with the bytes themselves. Without this the folder would still announce "block X
 * holds a PNG, 2.4 MB" in an ordinary directory listing — a smaller protection than §9.4.2 gives
 * every other payload, and a strange one to settle for once the bytes are already encrypted.
 *
 * **The uid deliberately stays in the clear.** It is what lets a fetch be driven by the folder
 * listing rather than by whichever pass happened to merge the page record, so an image and its
 * record may arrive in either order and a failed fetch is simply retried next pass. Hiding it
 * would cost that property and buy very little, since an encrypted `pages/<uid>.json` sits in the
 * same folder naming the same uid. What is worth hiding is the type and the size; what is not
 * worth breaking the fetch for is the association.
 *
 * Format: the original file name, a newline, then the raw bytes. A newline cannot occur in a name
 * that survived [safeImageFileName], which keeps only alphanumerics, `-` and a single `.` — so the
 * first newline is unambiguously the separator and no escaping is needed.
 *
 * Not used by `PortableArchive`, which encrypts the same images inside a zip. Renaming entries
 * there would buy nothing: an encrypted archive's entry list already names every page uid in the
 * clear, because `manifest.json` stays readable by design. Closing that is a different change to
 * a different container, and pretending otherwise by making one entry opaque would be theatre.
 */
internal object ImageEnvelope {
    private const val SEPARATOR: Byte = '\n'.code.toByte()

    fun wrap(name: String, bytes: ByteArray): ByteArray =
        name.toByteArray(Charsets.UTF_8) + SEPARATOR + bytes

    /**
     * The original name and the image bytes, or null when [payload] is not an envelope this build
     * can read.
     *
     * Null rather than an exception, and rather than a guess at the bytes: the caller treats it
     * exactly as it treats a failed decrypt — "could not read this image" — which is already the
     * right handling for a file written by a newer build in a format this one does not know.
     */
    fun unwrap(payload: ByteArray): Pair<String, ByteArray>? {
        val split = payload.indexOf(SEPARATOR)
        // `<= 0` and not `< 0`: an empty name is as unusable as a missing separator, and letting
        // it through would hand `LocalImageStore.write` a name that sanitises away to nothing.
        if (split <= 0) return null
        return payload.copyOfRange(0, split).toString(Charsets.UTF_8) to
            payload.copyOfRange(split + 1, payload.size)
    }
}
