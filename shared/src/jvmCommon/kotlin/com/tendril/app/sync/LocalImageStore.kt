package com.tendril.app.sync

/**
 * §9.4 / S4 — where *this device* keeps its copies of block images.
 *
 * The counterpart to [SyncFileStore], and deliberately a separate interface rather than a fourth
 * channel on it. They answer different questions and have different lifetimes: [SyncFileStore] is
 * the shared folder, which every device reads and writes and Syncthing replicates; this is
 * app-private storage, which nothing outside this process can see and no peer has any business
 * naming. `Block.imagePath` points here; [BlockSnapshotRecord.imageName] points there.
 *
 * Keeping them apart is what lets an image travel without a device dictating another's filesystem
 * layout — the folder agrees on *which* image, and each device resolves that to a path of its own.
 * A single combined interface would have made it easy to write the merge that copies a peer's
 * absolute path into local state, which is the bug the separation exists to make unspellable.
 *
 * Implementations own creating their directory on write and tolerating its absence on read.
 */
interface LocalImageStore {
    /**
     * The bytes at [path], or null when it no longer resolves.
     *
     * Null is an ordinary outcome, not an error: `Block.imagePath` is a plain string in a database
     * that outlives any particular file, so a path can survive a file that has been cleared,
     * restored from a backup taken elsewhere, or written by an install that has since been
     * replaced. A publish that cannot read the local file simply has nothing to publish.
     */
    suspend fun read(path: String): ByteArray?

    /**
     * Stores [bytes] under [name] and returns the local path to record in `Block.imagePath`.
     *
     * [name] is a folder-side name (`<block uid>.<extension>`), which is to say it arrived from
     * another device. Implementations must treat it as untrusted and confine the write to their
     * own directory — the same rule `DesktopFileSyncFileStore.resolveInside` enforces, and for the
     * same reason.
     */
    suspend fun write(name: String, bytes: ByteArray): String
}

/**
 * The one place a folder-side image name is made safe to use as a local file name.
 *
 * The name arrived from another device, so it is untrusted in the same sense a zip entry name is
 * (`NotionImporter.saveAsset` documents the identical rule for the identical reason): anything
 * that survives here becomes a path component, and a crafted `x/../../y` would place the file
 * outside the directory that is supposed to contain it.
 *
 * Shared between the two platform stores rather than written twice, because a sanitiser that
 * exists in two copies is a sanitiser that gets fixed in one of them.
 */
internal fun safeImageFileName(name: String): String {
    // No separator stripping is needed before this: the two filters below keep only alphanumerics
    // (and `-` in the stem), which removes `/`, `\` and `.` wherever they appear rather than only
    // where a `substringAfterLast` happened to look. Whitelisting is the reason a crafted
    // `../../x.png` cannot survive as a path component -- it reduces to `x.png`.
    val extension = name.substringAfterLast('.', "").take(8).filter { it.isLetterOrDigit() }
    val stem = name.substringBeforeLast('.').filter { it.isLetterOrDigit() || it == '-' }.take(64)
    // A name that sanitises away to nothing is not written under a guessed one -- the caller
    // treats an empty result as "this image cannot be stored" rather than inventing a path.
    if (stem.isEmpty()) return ""
    return if (extension.isEmpty()) stem else "$stem.$extension"
}
