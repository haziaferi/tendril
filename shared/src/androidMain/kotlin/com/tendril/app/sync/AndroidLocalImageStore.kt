package com.tendril.app.sync

import android.content.Context
import java.io.File

/**
 * §9.4 / S4 — [LocalImageStore] on Android: app-private `filesDir`, invisible to every other app
 * and to the synced folder.
 *
 * A directory of its own rather than the importer's `notion_import_assets`, which holds images
 * that arrived with a Notion export and are named by a random UUID. These arrive from a peer and
 * are named after the block they belong to. Two origins, two lifetimes, and mixing them would make
 * "which of these is still referenced" harder to answer when S4's purge step needs it.
 */
class AndroidLocalImageStore(private val context: Context) : LocalImageStore {

    private val dir: File get() = File(context.filesDir, DIR)

    override suspend fun read(path: String): ByteArray? =
        File(path).takeIf { it.isFile }?.let { runCatching { it.readBytes() }.getOrNull() }

    override suspend fun write(name: String, bytes: ByteArray): String {
        val safe = safeImageFileName(name)
        require(safe.isNotEmpty()) { "Image name '$name' is not usable as a file name." }
        val target = File(dir.apply { mkdirs() }, safe)
        target.writeBytes(bytes)
        return target.absolutePath
    }

    override suspend fun list(): List<String> =
        dir.listFiles()?.map { it.absolutePath }.orEmpty()

    override suspend fun delete(path: String) {
        // Confined to this store's directory even though the caller is this app: a path that came
        // out of a database row is a path that has outlived whatever wrote it, and `delete` is not
        // an operation to point at an arbitrary location on the strength of that.
        val file = File(path)
        if (file.parentFile?.canonicalPath == dir.canonicalPath) file.delete()
    }

    private companion object {
        const val DIR = "block_images"
    }
}
