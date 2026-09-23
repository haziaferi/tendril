package com.tendril.app.data

import android.database.sqlite.SQLiteDatabase
import com.tendril.app.data.entry.EntryKind
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * §9.10 — every declared migration, run over a **populated** file rather than an empty one.
 *
 * The audit of 2026-09-22 found that no JVM test opened a database at an older version with rows in
 * it: each migration had been walked on hardware, but the chain as a whole — v8 (the first version a
 * migration answers for) through the current one — had no proof that a row written before the
 * chain survives to the end of it. Two cases matter most and are pinned here:
 *
 * - [MIGRATION_9_10]'s backfill of `habit_completions` from a habit's two remembered dates, and
 *   [MIGRATION_8_9]'s per-row uid on reminders and completions (a shared uid would have made the
 *   unique index fail on any device with two reminders).
 * - [MIGRATION_19_20]'s rebuild of `entries` — a `DROP TABLE` on a table three others cascade
 *   from. If foreign keys were enforced during migration the drop would take the reminders,
 *   completions and time logs with it; that the counts hold proves they are not (Room turns
 *   `foreign_keys` on in `onOpen`, after the migrations have run).
 *
 * The starting file is built from the exported schema JSON (`shared/schemas/…/<version>.json`) —
 * the same DDL Room itself would have run on a device at that version — and opened through the
 * production Android path, [openTendrilDatabase], so the assertion `recovered == false` is the
 * assertion that the chain did not throw: a throwing migration would not crash here, it would set
 * the file aside and hand back an empty database (see [openOrRecover]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class PopulatedMigrationTest {

    @Test
    fun `a v8 file with an entry, a reminder, a completion, a habit with history and a page reaches v25 intact`() {
        val context = RuntimeEnvironment.getApplication()
        val file = context.getDatabasePath(TENDRIL_DB_NAME)
        createAtVersion(file, 8) { db ->
            db.execSQL("INSERT INTO entries (id, uid, title, kind, source, createdAt, updatedAt) VALUES (1, 'e-1', 'Dentist', 'TASK', 'MANUAL', 1000, 1000)")
            db.execSQL("INSERT INTO entries (id, uid, title, kind, source, createdAt, updatedAt) VALUES (2, 'e-2', 'Rent', 'TASK', 'MANUAL', 1000, 1000)")
            // Two reminders: v9 must give each its own uid or the unique index cannot be built.
            db.execSQL("INSERT INTO reminders (id, entryId, offset) VALUES (1, 1, 'PRESET:ONE_DAY')")
            db.execSQL("INSERT INTO reminders (id, entryId, offset) VALUES (2, 2, 'PRESET:ONE_DAY')")
            db.execSQL("INSERT INTO entry_completions (id, entryId, occurrenceDate, resolvedAt, status) VALUES (1, 1, 20000, 1000, 'DONE')")
            db.execSQL("INSERT INTO entry_completions (id, entryId, occurrenceDate, resolvedAt, status) VALUES (2, 2, 20001, 1000, 'DONE')")
            // A habit checked twice — the only history v8 kept, which v10 turns into two completion rows.
            db.execSQL("INSERT INTO habits (id, uid, title, frequency, streak, lastCompletedDate, previousStreak, previousCompletedDate, createdAt, updatedAt) VALUES (1, 'h-1', 'Stretch', '1:DAY', 2, 20001, 1, 20000, 1000, 2000)")
            db.execSQL("INSERT INTO pages (id, uid, title, kind, isTemplate, createdAt, updatedAt) VALUES (1, 'p-1', 'Notes', 'PAGE', 0, 1000, 1000)")
            db.execSQL("INSERT INTO blocks (id, uid, pageId, type, `order`, content, formattingSpans, toggleExpanded, createdAt, updatedAt) VALUES (1, 'b-1', 1, 'PARAGRAPH', 0, 'hello', '[]', 1, 1000, 1000)")
            // A search-index row for that page, so the `page_fts` assertion below has something to
            // be about: [MIGRATION_15_16] is `DELETE FROM page_fts`, and without a row seeded here
            // the table is empty at the end whether that migration ran or not.
            db.execSQL("INSERT INTO page_fts (docid, pageId, plainText) VALUES (1, 1, 'Notes hello')")
            db.rawQuery("SELECT COUNT(*) FROM page_fts", null).use { cursor ->
                cursor.moveToFirst()
                assertEquals("the page_fts seed did not land — the assertion below would be vacuous", 1, cursor.getInt(0))
            }
        }

        val open = openTendrilDatabase(context)
        try {
            assertFalse("a migration threw and the file was set aside", open.recovered)
            val db = open.database
            runBlocking {
                assertEquals(listOf("Dentist", "Rent"), db.entryDao().getAll().map { it.title })
                val reminders = db.reminderDao().getAll()
                assertEquals(2, reminders.size)
                assertEquals("each reminder has its own 36-char uid", 2, reminders.map { it.uid }.filter { it.length == 36 }.toSet().size)
                val completions = db.entryCompletionDao().getAll()
                assertEquals(2, completions.size)
                assertEquals(2, completions.map { it.uid }.filter { it.length == 36 }.toSet().size)
                val habitLog = db.habitCompletionDao().getAll()
                assertEquals("both remembered dates became completion rows", listOf(20000L, 20001L), habitLog.map { it.date.toEpochDay() }.sorted())
                assertEquals(listOf("Notes"), db.pageDao().getAll().map { it.title })
                assertEquals(listOf("hello"), db.blockDao().getForPage(1).map { it.content })
                // Neither FTS table is a migration's job to fill: v25 creates `block_fts` empty, and
                // [MIGRATION_15_16] *empties* `page_fts` — the only row-moving migration in the chain
                // whose result nothing else here observes. Both are rebuilt by the first launch's
                // `healIndex`, which is exactly why a page surviving the chain with no index row is
                // the correct end state rather than a lost one.
                assertTrue("block_fts exists after v25, and is empty", db.blockFtsDao().indexedPageIds().isEmpty())
                // The seeded row is gone, which is what proves MIGRATION_15_16 ran: the page itself
                // survived the chain (asserted above) while its index row did not.
                assertTrue("page_fts was emptied by the chain, and not repopulated", db.pageFtsDao().indexedPageIds().isEmpty())
            }
        } finally {
            open.database.close()
        }
    }

    @Test
    fun `a v19 file keeps its reminders, completions and time logs across the entries rebuild, and a flag becomes importance 3`() {
        val context = RuntimeEnvironment.getApplication()
        val file = context.getDatabasePath(TENDRIL_DB_NAME)
        createAtVersion(file, 19) { db ->
            db.execSQL("INSERT INTO entries (id, uid, title, kind, important, source, createdAt, updatedAt) VALUES (1, 'e-1', 'Flagged', 'TASK', 1, 'MANUAL', 1000, 1000)")
            db.execSQL("INSERT INTO entries (id, uid, title, kind, important, source, createdAt, updatedAt) VALUES (2, 'e-2', 'Plain', 'EVENT', 0, 'MANUAL', 1000, 1000)")
            db.execSQL("INSERT INTO reminders (id, uid, entryId, offset) VALUES (1, 'r-1', 1, 'PRESET:ONE_DAY')")
            db.execSQL("INSERT INTO entry_completions (id, uid, entryId, occurrenceDate, resolvedAt, status) VALUES (1, 'c-1', 1, 20000, 1000, 'DONE')")
            db.execSQL("INSERT INTO time_logs (id, uid, entryId, startedAt, endedAt, updatedAt) VALUES (1, 't-1', 1, 1000, 2000, 2000)")
        }

        val open = openTendrilDatabase(context)
        try {
            assertFalse("a migration threw and the file was set aside", open.recovered)
            val db = open.database
            runBlocking {
                val entries = db.entryDao().getAll().sortedBy { it.id }
                assertEquals(listOf("Flagged", "Plain"), entries.map { it.title })
                assertEquals(listOf(EntryKind.TASK, EntryKind.EVENT), entries.map { it.kind })
                assertEquals("a set flag lands on high", listOf(3, 0), entries.map { it.importance })
                assertEquals(listOf("r-1"), db.reminderDao().getAll().map { it.uid })
                assertEquals(listOf("c-1"), db.entryCompletionDao().getAll().map { it.uid })
                assertEquals(listOf("t-1"), db.timeLogDao().getAll().map { it.uid })
            }
        } finally {
            open.database.close()
        }
    }

    /**
     * Writes [file] as Room would have written it at [version]: every table and index from the
     * exported schema, the `room_master_table` row, and `user_version`. [populate] then adds rows in
     * that version's own column set.
     */
    private fun createAtVersion(file: File, version: Int, populate: (SQLiteDatabase) -> Unit) {
        val schema = JSONObject(schemaFile(version).readText(Charsets.UTF_8)).getJSONObject("database")
        file.parentFile!!.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("PRAGMA user_version = $version")
            populate(db)
        } finally {
            db.close()
        }
    }

    /** `shared/schemas/…/<version>.json`, found by walking up from the module's working directory. */
    private fun schemaFile(version: Int): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "shared/schemas/com.tendril.app.data.TendrilDatabase/$version.json")
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("no exported schema for v$version under any ancestor of ${File("").absolutePath}")
    }
}
