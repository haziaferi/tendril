package com.tendril.app.sync

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Scaffolding that lets the archive classes run in a plain JVM unit test.
 *
 * `PortableArchive` and `NotionImporter` both reach for `Context.contentResolver` plus
 * `filesDir`/`cacheDir`. Rather than pulling in Robolectric, the Context is a mockk whose
 * resolver is backed by in-memory byte arrays and whose directories are real temp folders — so
 * assertions about *where files actually landed* are about the real filesystem, which is the
 * point for the asset-staging and path-traversal tests.
 */

/** A resolver whose reads and writes are wired to in-memory buffers, keyed by Uri instance. */
class FakeContentResolverBacking {
    private val readable = mutableMapOf<Uri, ByteArray>()
    private val written = mutableMapOf<Uri, ByteArrayOutputStream>()
    private val unwritable = mutableSetOf<Uri>()

    val resolver: ContentResolver = mockk()

    init {
        // `answers`, not `returns`: each call must hand back a fresh stream, since the
        // importer consumes it and some paths open the same Uri more than once.
        every { resolver.openInputStream(any()) } answers {
            val uri = firstArg<Uri>()
            readable[uri]?.let { ByteArrayInputStream(it) }
        }
        every { resolver.openOutputStream(any()) } answers {
            val uri = firstArg<Uri>()
            if (uri in unwritable) null else written.getOrPut(uri) { ByteArrayOutputStream() }
        }
    }

    fun givenFile(bytes: ByteArray): Uri = mockk<Uri>().also { readable[it] = bytes }

    fun writableFile(): Uri = mockk()

    /** Models a provider that refuses to hand over an output stream. */
    fun unwritableFile(): Uri = mockk<Uri>().also { unwritable += it }

    fun bytesWrittenTo(uri: Uri): ByteArray = written[uri]?.toByteArray() ?: ByteArray(0)
}

/** A Context that exposes [backing]'s resolver and the given directories. */
fun fakeContext(backing: FakeContentResolverBacking, filesDir: File, cacheDir: File): Context =
    mockk<Context>().also {
        every { it.contentResolver } returns backing.resolver
        every { it.filesDir } returns filesDir
        every { it.cacheDir } returns cacheDir
        every { it.applicationContext } returns it
    }

/** Builds a zip in memory from `name to content` pairs. */
fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for ((name, bytes) in entries) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

fun zipOfText(vararg entries: Pair<String, String>): ByteArray =
    zipOf(*entries.map { (name, text) -> name to text.toByteArray(Charsets.UTF_8) }.toTypedArray())

/**
 * Reads a zip back into `name to bytes` — the inverse of [zipOf], for asserting on what an export
 * actually packaged.
 *
 * Bytes rather than text, deliberately: since S4 an archive's `images/` entries are not text, and a
 * helper that handed back Strings would quietly corrupt exactly the entries these tests exist to
 * check. The JSON ones are `String(bytes)` at the call site where that is what is wanted.
 */
fun entriesOf(zipBytes: ByteArray): Map<String, ByteArray> {
    val result = linkedMapOf<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) result[entry.name] = zip.readBytes()
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
    return result
}
