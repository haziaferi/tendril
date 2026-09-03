package com.tendril.app.sync

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * On-device tests for SYNC-02 — the SAF write path.
 *
 * These can't run on the JVM: the whole question is how `DocumentFile` behaves, and the fix
 * replaced a delete-then-rename (which lost the file if anything went wrong in between) with a
 * write-temp / move-aside / rename / drop-backup sequence. That sequence leans on `renameTo`
 * working twice in a row, which is exactly the thing worth checking against a real device
 * rather than a mock.
 *
 * The store is built over a `DocumentFile` rooted at the app's own cache directory. That
 * exercises the real `writeAtomic` logic against a real filesystem without needing a user to
 * grant a SAF tree through the picker first.
 */
class SafWriteInstrumentedTest {

    private lateinit var context: Context
    private lateinit var folder: File
    private lateinit var store: AndroidSafSyncFileStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        folder = File(context.cacheDir, "saf_test_${System.nanoTime()}").apply { mkdirs() }
        store = AndroidSafSyncFileStore.forDocumentFile(context, DocumentFile.fromFile(folder))
    }

    private fun namesInFolder(): List<String> = folder.listFiles().orEmpty().map { it.name }.sorted()

    @Test
    fun writesAndReadsBackAFile() = runBlocking {
        store.writeRoot("entries_active.json", """[{"uid":"a"}]""".toByteArray())

        assertEquals(
            """[{"uid":"a"}]""",
            store.readRoot("entries_active.json")?.toString(Charsets.UTF_8),
        )
    }

    @Test
    fun replacesAnExistingFileAndLeavesNoTempOrBackupBehind() = runBlocking {
        store.writeRoot("entries_active.json", "first".toByteArray())
        store.writeRoot("entries_active.json", "second".toByteArray())

        assertEquals("second", store.readRoot("entries_active.json")?.toString(Charsets.UTF_8))
        // The move-aside dance must clean up after itself — a .bak or .tmp left in a synced
        // folder would be replicated to every other device by Syncthing.
        assertEquals(listOf("entries_active.json"), namesInFolder())
    }

    @Test
    fun repeatedWritesNeverLeaveTheDestinationMissing() = runBlocking {
        // The property the old code violated: there was a window, after the destination was
        // deleted and before the rename landed, where the snapshot simply did not exist.
        repeat(15) { i ->
            store.writeRoot("entries_active.json", "payload-$i".toByteArray())
            assertTrue(
                "destination missing after write $i",
                File(folder, "entries_active.json").exists(),
            )
        }
        assertEquals("payload-14", store.readRoot("entries_active.json")?.toString(Charsets.UTF_8))
        assertEquals(listOf("entries_active.json"), namesInFolder())
    }

    @Test
    fun writesPagesIntoTheirOwnSubdirectory() = runBlocking {
        store.writePage("abc-123.json", """{"uid":"abc-123"}""".toByteArray())

        assertEquals("""{"uid":"abc-123"}""", store.readPage("abc-123.json")?.toString(Charsets.UTF_8))
        assertEquals(listOf("pages"), namesInFolder())
        assertEquals(listOf("abc-123.json"), store.listPages().sorted())
    }

    @Test
    fun listRootSeesWrittenFilesAndNotDeletedOnes() = runBlocking {
        store.writeRoot("habits.json", "[]".toByteArray())
        store.writeRoot("entries_active.json", "[]".toByteArray())
        assertEquals(listOf("entries_active.json", "habits.json"), store.listRoot().sorted())

        store.deleteRoot("habits.json")
        assertEquals(listOf("entries_active.json"), store.listRoot().sorted())
        assertNull(store.readRoot("habits.json"))
    }

    @Test
    fun readingAMissingFileReturnsNullRatherThanThrowing() = runBlocking {
        assertNull(store.readRoot("never_written.json"))
        assertNull(store.readPage("never_written.json"))
        assertEquals(emptyList<String>(), store.listPages())
    }

    @Test
    fun writeSurvivesAPreExistingStaleTempFile() = runBlocking {
        // An interrupted earlier write can leave <name>.tmp behind; the next write must reclaim
        // it rather than failing because the name is taken.
        File(folder, "entries_active.json.tmp").writeText("leftover from a killed process")

        store.writeRoot("entries_active.json", "clean".toByteArray())

        assertEquals("clean", store.readRoot("entries_active.json")?.toString(Charsets.UTF_8))
        assertEquals(listOf("entries_active.json"), namesInFolder())
    }

    @Test
    fun largePayloadRoundTrips() = runBlocking {
        // A real snapshot of a busy workspace is not a few bytes.
        val payload = ByteArray(3 * 1024 * 1024) { (it % 251).toByte() }
        store.writeRoot("entries_active.json", payload)

        val read = store.readRoot("entries_active.json")
        assertEquals(payload.size, read?.size)
        assertTrue("payload differs after round trip", payload.contentEquals(read!!))
    }
}
