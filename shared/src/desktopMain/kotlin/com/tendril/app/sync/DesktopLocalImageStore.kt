package com.tendril.app.sync

import java.io.File

/**
 * §9.4 / S4 — [LocalImageStore] on desktop: a plain directory, supplied by the caller for the same
 * reason `DesktopFileSyncFileStore` takes its root rather than deciding one.
 *
 * Desktop had no image handling of any kind before S4 — nothing wrote `Block.imagePath` and
 * nothing read it — so this is the first place the platform keeps one.
 */
class DesktopLocalImageStore(private val dir: File) : LocalImageStore {

    override suspend fun read(path: String): ByteArray? =
        File(path).takeIf { it.isFile }?.let { runCatching { it.readBytes() }.getOrNull() }

    override suspend fun write(name: String, bytes: ByteArray): String {
        val safe = safeImageFileName(name)
        require(safe.isNotEmpty()) { "Image name '$name' is not usable as a file name." }
        dir.mkdirs()
        val target = File(dir, safe)
        target.writeBytes(bytes)
        return target.absolutePath
    }
}
