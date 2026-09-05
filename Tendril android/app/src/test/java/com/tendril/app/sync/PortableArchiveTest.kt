package com.tendril.app.sync

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.page.PageDao
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

/**
 * Regression tests for ARCH-01 — "Restore backup" wiping local data before it knew whether the
 * archive was readable.
 *
 * Every decode in this class is best-effort (`runCatching { ... }.getOrNull()`), so a truncated
 * download, another app's zip, or a future schema all decoded to "nothing" — and the wipe had
 * already happened by then, leaving an empty database and no way back. The rule these tests pin:
 * **nothing is deleted until the archive has proven it carries readable records.**
 */
class PortableArchiveTest {

    @get:Rule val temp = TemporaryFolder()

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun archive(entryDao: FakeEntryDao, habitDao: FakeHabitDao, backing: FakeContentResolverBacking) =
        PortableArchive(
            context = fakeContext(backing, temp.newFolder(), temp.newFolder()),
            entryDao = entryDao,
            habitDao = habitDao,
            pageDao = mockk<PageDao>(relaxed = true),
            purgeRegistry = mockk(relaxed = true),
            pagesSyncEngine = mockk(relaxed = true),
        )

    private fun localEntry(uid: String, title: String) = Entry(
        id = 1,
        uid = uid,
        title = title,
        kind = EntryKind.TASK,
        startDate = null, startTime = null, endDate = null, endTime = null,
        recurrenceRule = null,
        status = EntryStatus.PENDING,
        createdAt = Instant.ofEpochMilli(1_000),
        updatedAt = Instant.ofEpochMilli(1_000),
    )

    private fun entryRecord(uid: String, title: String, updatedAt: Long = 5_000L) = EntrySnapshotRecord(
        uid = uid, title = title, kind = "TASK", status = "PENDING",
        createdAt = updatedAt, updatedAt = updatedAt,
    )

    private fun validArchiveBytes(vararg records: EntrySnapshotRecord) = zipOfText(
        "manifest.json" to """{"appVersion":"0.1.0","exportedAtEpochMillis":1,"kind":"full","includedFiles":[]}""",
        "entries_active.json" to json.encodeToString(records.toList()),
    )

    // ---------------------------------------------------------------- ARCH-01

    @Test
    fun `restore refuses a corrupt archive and leaves local data untouched`() = runBlocking {
        val entryDao = FakeEntryDao(listOf(localEntry("local-1", "Precious local task")))
        val habitDao = FakeHabitDao()
        val backing = FakeContentResolverBacking()
        val uri = backing.givenFile(zipOfText("entries_active.json" to """[{"uid":"x","tit"""))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { archive(entryDao, habitDao, backing).restoreFromBackup(uri) }
        }

        assertEquals(
            "a restore that could not read the archive must not have deleted anything",
            listOf("Precious local task"),
            entryDao.getAll().map { it.title },
        )
    }

    @Test
    fun `restore refuses an archive from another app and leaves local data untouched`() = runBlocking {
        val entryDao = FakeEntryDao(listOf(localEntry("local-1", "Precious local task")))
        val backing = FakeContentResolverBacking()
        val uri = backing.givenFile(zipOfText("photos/holiday.txt" to "not a Tendril archive at all"))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { archive(entryDao, FakeHabitDao(), backing).restoreFromBackup(uri) }
        }

        assertEquals(listOf("Precious local task"), entryDao.getAll().map { it.title })
    }

    @Test
    fun `restore refuses an empty archive and leaves local data untouched`() = runBlocking {
        val entryDao = FakeEntryDao(listOf(localEntry("local-1", "Precious local task")))
        val backing = FakeContentResolverBacking()
        val uri = backing.givenFile(zipOf())

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { archive(entryDao, FakeHabitDao(), backing).restoreFromBackup(uri) }
        }

        assertEquals(listOf("Precious local task"), entryDao.getAll().map { it.title })
    }

    @Test
    fun `restore refuses an archive whose only file is a manifest`() = runBlocking {
        // A manifest alone proves the file is ours, not that it carries any data.
        val entryDao = FakeEntryDao(listOf(localEntry("local-1", "Precious local task")))
        val backing = FakeContentResolverBacking()
        val uri = backing.givenFile(
            zipOfText("manifest.json" to """{"appVersion":"0.1.0","exportedAtEpochMillis":1,"kind":"full","includedFiles":[]}""")
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { archive(entryDao, FakeHabitDao(), backing).restoreFromBackup(uri) }
        }

        assertEquals(listOf("Precious local task"), entryDao.getAll().map { it.title })
    }

    @Test
    fun `restore does replace local data when the archive is readable`() = runBlocking {
        // The mirror of the refusals above — the guard must not have turned Restore into a no-op.
        val entryDao = FakeEntryDao(listOf(localEntry("local-1", "Task that should be erased")))
        val backing = FakeContentResolverBacking()
        val uri = backing.givenFile(validArchiveBytes(entryRecord("backup-1", "Task from the backup")))

        archive(entryDao, FakeHabitDao(), backing).restoreFromBackup(uri)

        assertEquals(
            listOf("Task from the backup"),
            entryDao.getAll().map { it.title },
        )
    }

    // ---------------------------------------------------------------- import / export

    @Test
    fun `import is additive and never erases local records`() = runBlocking {
        val entryDao = FakeEntryDao(listOf(localEntry("local-1", "Existing task")))
        val backing = FakeContentResolverBacking()
        val uri = backing.givenFile(validArchiveBytes(entryRecord("backup-1", "Imported task")))

        val result = archive(entryDao, FakeHabitDao(), backing).importAdditive(uri)

        assertEquals(
            setOf("Existing task", "Imported task"),
            entryDao.getAll().map { it.title }.toSet(),
        )
        assertTrue(result.hadManifest)
        assertEquals(1, result.entryFilesFound)
    }

    @Test
    fun `import of a corrupt archive reports nothing rather than throwing`() = runBlocking {
        // Import is the everyday path and stays best-effort: unlike Restore it deletes nothing,
        // so a bad file has nothing to guard against.
        val entryDao = FakeEntryDao(listOf(localEntry("local-1", "Existing task")))
        val backing = FakeContentResolverBacking()
        val uri = backing.givenFile(zipOfText("entries_active.json" to "}{ garbage"))

        archive(entryDao, FakeHabitDao(), backing).importAdditive(uri)

        assertEquals(listOf("Existing task"), entryDao.getAll().map { it.title })
    }

    @Test
    fun `export throws instead of silently writing nothing when the provider refuses a stream`() {
        val backing = FakeContentResolverBacking()
        val uri = backing.unwritableFile()

        assertThrows(IllegalStateException::class.java) {
            runBlocking { archive(FakeEntryDao(), FakeHabitDao(), backing).export(uri) }
        }
    }

    @Test
    fun `exported archive round-trips back through import`() = runBlocking {
        val source = FakeEntryDao(listOf(localEntry("local-1", "Round trip me")))
        val backing = FakeContentResolverBacking()
        val destination = backing.writableFile()

        archive(source, FakeHabitDao(), backing).export(destination)
        val exported = backing.bytesWrittenTo(destination)
        assertTrue("export produced no bytes", exported.isNotEmpty())

        val restored = FakeEntryDao()
        val reread = backing.givenFile(exported)
        archive(restored, FakeHabitDao(), backing).importAdditive(reread)

        assertEquals(listOf("Round trip me"), restored.getAll().map { it.title })
    }
}
