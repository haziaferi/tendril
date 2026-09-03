package com.tendril.app.sync

import android.content.Context
import androidx.documentfile.provider.RenameGateDocumentFile
import androidx.documentfile.provider.DocumentFile
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * The discriminating test for SYNC-02.
 *
 * [SafWriteInstrumentedTest] proves the new write sequence works on a real device, but it can't
 * tell the fixed code from the broken code: both end up with the right bytes when every step
 * succeeds. The loss window was only observable when something failed *between* removing the old
 * file and putting the new one in place.
 *
 * So this test forces that failure. A `DocumentFile` wrapper refuses the temp → destination
 * rename, and the assertion is that the previous snapshot is still readable afterwards:
 *
 *  - fixed code:  old file was moved to `.bak`, rename fails, `.bak` is restored → content intact
 *  - broken code: old file was deleted outright, rename fails → **the snapshot is gone**
 */
class SafWriteFailureInstrumentedTest {

    private lateinit var context: Context
    private lateinit var folder: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        folder = File(context.cacheDir, "saf_fail_${System.nanoTime()}").apply { mkdirs() }
    }

    private companion object {
        const val NAME = "entries_active.json"
        const val ORIGINAL = """[{"uid":"keep-me","title":"The only copy"}]"""
    }

    @Test
    fun aFailedRenameLeavesThePreviousSnapshotIntact() = runBlocking {
        // Seed a good snapshot through the ordinary path.
        val healthy = AndroidSafSyncFileStore.forDocumentFile(context, DocumentFile.fromFile(folder))
        healthy.writeRoot(NAME, ORIGINAL.toByteArray())
        assertEquals(ORIGINAL, healthy.readRoot(NAME)?.toString(Charsets.UTF_8))

        // Now write again, with the temp -> destination rename refused. The move-aside rename
        // (to "<name>.bak") is still allowed, so the failure lands exactly in the window the
        // old code left unguarded.
        val gated = AndroidSafSyncFileStore.forDocumentFile(
            context,
            RenameGateDocumentFile(DocumentFile.fromFile(folder)) { from, target -> target == NAME && from != "$NAME.bak" },
        )

        assertThrows(IllegalStateException::class.java) {
            runBlocking { gated.writeRoot(NAME, """[{"uid":"new"}]""".toByteArray()) }
        }

        assertEquals(
            "the previous snapshot must survive a failed replace — this is the data-loss window",
            ORIGINAL,
            healthy.readRoot(NAME)?.toString(Charsets.UTF_8),
        )
    }

    @Test
    fun aFailedMoveAsideLeavesThePreviousSnapshotIntact() = runBlocking {
        val healthy = AndroidSafSyncFileStore.forDocumentFile(context, DocumentFile.fromFile(folder))
        healthy.writeRoot(NAME, ORIGINAL.toByteArray())

        // Refuse the *first* rename instead: the old file can't be moved aside, so the write
        // must abort before touching it at all.
        val gated = AndroidSafSyncFileStore.forDocumentFile(
            context,
            RenameGateDocumentFile(DocumentFile.fromFile(folder)) { _, target -> target == "$NAME.bak" },
        )

        assertThrows(IllegalStateException::class.java) {
            runBlocking { gated.writeRoot(NAME, """[{"uid":"new"}]""".toByteArray()) }
        }

        assertEquals(ORIGINAL, healthy.readRoot(NAME)?.toString(Charsets.UTF_8))
    }

    @Test
    fun aFailedWriteDoesNotLeaveTempFilesBehind() = runBlocking {
        val healthy = AndroidSafSyncFileStore.forDocumentFile(context, DocumentFile.fromFile(folder))
        healthy.writeRoot(NAME, ORIGINAL.toByteArray())

        val gated = AndroidSafSyncFileStore.forDocumentFile(
            context,
            RenameGateDocumentFile(DocumentFile.fromFile(folder)) { from, target -> target == NAME && from != "$NAME.bak" },
        )
        runCatching { gated.writeRoot(NAME, "ignored".toByteArray()) }

        // A synced folder shouldn't accumulate debris from failed writes.
        assertEquals(
            listOf(NAME),
            folder.listFiles().orEmpty().map { it.name }.sorted(),
        )
    }
}
