package com.tendril.app.sync

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.completion.EntryCompletion
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.reminder.Reminder
import com.tendril.app.data.reminder.ReminderOffset
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
import java.time.LocalDate

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

    private fun archive(
        entryDao: FakeEntryDao,
        habitDao: FakeHabitDao,
        backing: FakeContentResolverBacking,
        passphrase: String? = null,
        reminderDao: FakeReminderDao = FakeReminderDao(),
        entryCompletionDao: FakeEntryCompletionDao = FakeEntryCompletionDao(),
        habitCompletionDao: FakeHabitCompletionDao = FakeHabitCompletionDao(),
    ) = PortableArchive(
        context = fakeContext(backing, temp.newFolder(), temp.newFolder()),
        entryDao = entryDao,
        habitDao = habitDao,
        pageDao = mockk<PageDao>(relaxed = true),
        reminderDao = reminderDao,
        entryCompletionDao = entryCompletionDao,
        habitCompletionDao = habitCompletionDao,
        purgeRegistry = mockk(relaxed = true),
        pagesSyncEngine = mockk(relaxed = true),
        localImages = InMemoryLocalImageStore(),
        passphrase = { passphrase },
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

    /**
     * S2's other half of the acceptance test: "`.tendril` export unzips with a `reminders` and
     * `entry_completions` array".
     *
     * The tombstone assertion is the part worth having. An archive is the one place a *deleted*
     * reminder could quietly come back even with the folder sync doing the right thing: exporting
     * only live rows would produce a file that, imported onto a device that still held the
     * reminder, says nothing about the delete — and `AlarmScheduler` would go on firing it.
     */
    @Test
    fun `an exported archive carries reminders and completions, and a delete survives the round trip`() = runBlocking {
        val entry = localEntry("e1", "Task with reminders")
        val backing = FakeContentResolverBacking()
        val destination = backing.writableFile()

        val sourceReminders = FakeReminderDao(
            listOf(
                archivedReminder(1, "r-live", entry.id),
                archivedReminder(2, "r-gone", entry.id, deletedAt = Instant.ofEpochMilli(2_000)),
            )
        )
        val sourceCompletions = FakeEntryCompletionDao(listOf(archivedCompletion(1, "c1", entry.id)))

        archive(
            FakeEntryDao(listOf(entry)), FakeHabitDao(), backing,
            reminderDao = sourceReminders, entryCompletionDao = sourceCompletions,
        ).export(destination)

        val written = unzip(backing.bytesWrittenTo(destination))
        assertTrue("no reminders.json in the archive", written.containsKey("reminders.json"))
        assertTrue("no entry_completions.json in the archive", written.containsKey("entry_completions.json"))

        val restoredReminders = FakeReminderDao()
        val restoredCompletions = FakeEntryCompletionDao()
        val reread = backing.givenFile(backing.bytesWrittenTo(destination))
        archive(
            FakeEntryDao(listOf(entry)), FakeHabitDao(), backing,
            reminderDao = restoredReminders, entryCompletionDao = restoredCompletions,
        ).importAdditive(reread)

        // Both reminders travel; only the live one is visible to anything that schedules alarms.
        assertEquals(2, restoredReminders.getAll().size)
        assertEquals(listOf("r-live"), restoredReminders.getForEntry(entry.id).map { it.uid })
        assertEquals(Instant.ofEpochMilli(2_000), restoredReminders.getByUid("r-gone")!!.deletedAt)

        assertEquals(listOf("c1"), restoredCompletions.getAll().map { it.uid })
    }

    /** Append-only means importing the same archive twice must add nothing the second time. */
    @Test
    fun `importing an archive twice does not duplicate its completions`() = runBlocking {
        val entry = localEntry("e1", "Task")
        val backing = FakeContentResolverBacking()
        val destination = backing.writableFile()

        archive(
            FakeEntryDao(listOf(entry)), FakeHabitDao(), backing,
            entryCompletionDao = FakeEntryCompletionDao(listOf(archivedCompletion(1, "c1", entry.id))),
            habitCompletionDao = FakeHabitCompletionDao(),
        ).export(destination)

        val exported = backing.bytesWrittenTo(destination)
        val restoredCompletions = FakeEntryCompletionDao()
        repeat(2) {
            archive(
                FakeEntryDao(listOf(entry)), FakeHabitDao(), backing,
                entryCompletionDao = restoredCompletions,
                habitCompletionDao = FakeHabitCompletionDao(),
            ).importAdditive(backing.givenFile(exported))
        }

        assertEquals(1, restoredCompletions.getAll().size)
    }

    private fun archivedReminder(id: Long, uid: String, entryId: Long, deletedAt: Instant? = null) = Reminder(
        id = id,
        uid = uid,
        entryId = entryId,
        offset = ReminderOffset.FromPreset(ReminderOffset.Preset.ONE_DAY),
        deletedAt = deletedAt,
    )

    private fun archivedCompletion(id: Long, uid: String, entryId: Long) = EntryCompletion(
        id = id,
        uid = uid,
        entryId = entryId,
        occurrenceDate = LocalDate.of(2026, 9, 9),
        resolvedAt = Instant.ofEpochMilli(1_500),
        status = EntryStatus.DONE,
    )

    // -------------------------------------------------- §9.4.2 at-rest export encryption

    /** Reads a written `.tendril` back as raw per-entry bytes, so a test can assert on the
     * ciphertext rather than only on what round-trips. */
    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = mutableMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) out[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return out
    }

    @Test
    fun `with a passphrase set, every payload entry is encrypted and the manifest is not`() = runBlocking {
        val backing = FakeContentResolverBacking()
        val destination = backing.writableFile()
        val source = FakeEntryDao(listOf(localEntry("uid-1", "Medical appointment")))

        val result = archive(source, FakeHabitDao(), backing, passphrase = PASSPHRASE).export(destination)

        assertTrue("export must report that it encrypted", result.encrypted)
        val written = unzip(backing.bytesWrittenTo(destination))
        assertTrue(
            "the manifest stays readable so an importer can say what the file is",
            !SnapshotEncryption.isEncrypted(written.getValue("manifest.json")),
        )
        val manifest = json.decodeFromString<TendrilManifest>(
            written.getValue("manifest.json").toString(Charsets.UTF_8)
        )
        assertTrue("the manifest must declare itself encrypted", manifest.encrypted)
        for ((name, bytes) in written) {
            if (name == "manifest.json") continue
            assertTrue("$name should be ciphertext", SnapshotEncryption.isEncrypted(bytes))
        }
        // The point of the exercise: the title must not be sitting in the file in the clear.
        assertTrue(
            "no payload may contain the plaintext title",
            written.filterKeys { it != "manifest.json" }
                .none { (_, b) -> b.toString(Charsets.UTF_8).contains("Medical appointment") },
        )
    }

    @Test
    fun `an encrypted export round-trips with the same passphrase`() = runBlocking {
        val backing = FakeContentResolverBacking()
        val destination = backing.writableFile()
        val source = FakeEntryDao(listOf(localEntry("uid-1", "Round trip me")))
        archive(source, FakeHabitDao(), backing, passphrase = PASSPHRASE).export(destination)

        val reread = backing.givenFile(backing.bytesWrittenTo(destination))
        val restored = FakeEntryDao()
        archive(restored, FakeHabitDao(), backing, passphrase = PASSPHRASE).importAdditive(reread)

        assertEquals(listOf("Round trip me"), restored.getAll().map { it.title })
    }

    @Test
    fun `without a passphrase, an export stays plaintext`() = runBlocking {
        // §9.4.2 is off by default and this must stay true — encryption follows the toggle,
        // it isn't switched on by the existence of this code.
        val backing = FakeContentResolverBacking()
        val destination = backing.writableFile()
        val source = FakeEntryDao(listOf(localEntry("uid-1", "Plain as day")))

        val result = archive(source, FakeHabitDao(), backing).export(destination)

        assertTrue(!result.encrypted)
        val written = unzip(backing.bytesWrittenTo(destination))
        assertTrue("no entry may be ciphertext", written.none { (_, b) -> SnapshotEncryption.isEncrypted(b) })
        // `encrypted = false` equals the property's default, and kotlinx.serialization omits
        // those — so this asserts the default reads back correctly from a manifest that has no
        // `encrypted` key at all, which is also what every archive written before the field
        // existed looks like.
        val manifest = json.decodeFromString<TendrilManifest>(
            written.getValue("manifest.json").toString(Charsets.UTF_8)
        )
        assertTrue("the manifest must not claim encryption", !manifest.encrypted)
    }

    @Test
    fun `a plaintext archive still imports when a passphrase is set`() = runBlocking {
        // Turning encryption on must not orphan the exports made before it.
        val backing = FakeContentResolverBacking()
        val uri = backing.givenFile(validArchiveBytes(entryRecord("uid-1", "Made before encryption")))
        val restored = FakeEntryDao()

        archive(restored, FakeHabitDao(), backing, passphrase = PASSPHRASE).importAdditive(uri)

        assertEquals(listOf("Made before encryption"), restored.getAll().map { it.title })
    }

    @Test
    fun `restoring an encrypted archive with the wrong passphrase changes nothing`() = runBlocking {
        // The dangerous path: restore wipes before it applies, so an undecryptable archive has
        // to be refused *before* the delete, not discovered as "nothing decoded" after it.
        val backing = FakeContentResolverBacking()
        val destination = backing.writableFile()
        archive(FakeEntryDao(listOf(localEntry("uid-1", "In the backup"))), FakeHabitDao(), backing, PASSPHRASE)
            .export(destination)
        val reread = backing.givenFile(backing.bytesWrittenTo(destination))
        val local = FakeEntryDao(listOf(localEntry("local-1", "Precious local task")))

        val thrown = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { archive(local, FakeHabitDao(), backing, passphrase = "a different one").restoreFromBackup(reread) }
        }

        assertTrue(
            "the message must point at the passphrase, not at the file: ${thrown.message}",
            thrown.message!!.contains("passphrase"),
        )
        assertEquals(listOf("Precious local task"), local.getAll().map { it.title })
    }

    @Test
    fun `importing an encrypted archive with no passphrase set says so`() = runBlocking {
        val backing = FakeContentResolverBacking()
        val destination = backing.writableFile()
        archive(FakeEntryDao(listOf(localEntry("uid-1", "Locked"))), FakeHabitDao(), backing, PASSPHRASE)
            .export(destination)
        val reread = backing.givenFile(backing.bytesWrittenTo(destination))
        val restored = FakeEntryDao()

        val thrown = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { archive(restored, FakeHabitDao(), backing).importAdditive(reread) }
        }

        assertTrue(thrown.message!!.contains("Set your sync passphrase"))
        assertTrue("nothing may have been imported", restored.getAll().isEmpty())
    }

    private companion object {
        const val PASSPHRASE = "a shared passphrase"
    }
}
