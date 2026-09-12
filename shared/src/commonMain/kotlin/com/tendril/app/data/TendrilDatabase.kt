package com.tendril.app.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.SQLiteDriver
import com.tendril.app.data.canvas.CanvasEdge
import com.tendril.app.data.canvas.CanvasEdgeDao
import com.tendril.app.data.canvas.CanvasNode
import com.tendril.app.data.canvas.CanvasNodeDao
import com.tendril.app.data.canvas.PageCanvas
import com.tendril.app.data.canvas.PageCanvasDao
import com.tendril.app.data.completion.EntryCompletion
import com.tendril.app.data.completion.EntryCompletionDao
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitCompletion
import com.tendril.app.data.habit.HabitCompletionDao
import com.tendril.app.data.habit.HabitDao
import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockDao
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.page.PageFtsDao
import com.tendril.app.data.page.PageFtsEntry
import com.tendril.app.data.page.PageRelation
import com.tendril.app.data.page.PageRelationDao
import com.tendril.app.data.page.PageLabel
import com.tendril.app.data.page.Label
import com.tendril.app.data.page.LabelDao
import com.tendril.app.data.pagedatabase.PageDatabase
import com.tendril.app.data.pagedatabase.PageDatabaseDao
import com.tendril.app.data.pagedatabase.PageDatabaseView
import com.tendril.app.data.pagedatabase.PageDatabaseViewDao
import com.tendril.app.data.pagedatabase.Property
import com.tendril.app.data.pagedatabase.PropertyDao
import com.tendril.app.data.pagedatabase.PropertyValue
import com.tendril.app.data.pagedatabase.PropertyValueDao
import com.tendril.app.data.purge.PurgedRecord
import com.tendril.app.data.purge.PurgedRecordDao
import com.tendril.app.data.reminder.Reminder
import com.tendril.app.data.reminder.ReminderDao
import com.tendril.app.data.track.TimeLog
import com.tendril.app.data.track.TimeLogDao
import kotlinx.coroutines.Dispatchers

/**
 * §9.10 (Decided 2026-08-26, revised at v9) — Room migration policy. v1–v8 were
 * `fallbackToDestructiveMigration()` pre-v1: nothing stored was anything but trivially
 * re-creatable and the schema was still visibly moving. **v9 is the first version with a
 * real migration path** ([MIGRATION_8_9], S2), so a bump from v8 preserves data instead of
 * clearing it.
 *
 * The destructive fallback below is **narrowed to the pre-release schemas** (S1b, 2026-09-09):
 * it answers for a database still at v1–v8 and for nothing else. Every other unhandled case —
 * a bump whose migration was forgotten, a downgrade, a schema hash that moved without its
 * version — now reaches [openOrRecover], which preserves the file instead of destroying it.
 * See [PRE_RELEASE_VERSIONS] for why the pre-release versions are the exception rather than
 * the rule. The replacement was verified on hardware before this narrowing, which is what
 * §9.10's acceptance criterion asks for and what kept S1b open until then.
 *
 * §12.5 — moved into `:shared` (KMP): the platform-specific builder (`Context` on Android,
 * a file path on desktop) lives in each target's own source set; `@ConstructedBy` lets Room's
 * KSP codegen supply the per-platform constructor Room needs to build this class outside of
 * Android's Context-bound APIs.
 */
@Database(
    entities = [
        Entry::class, Habit::class, Reminder::class, EntryCompletion::class, HabitCompletion::class,
        Page::class, Label::class, PageLabel::class, Block::class, PageFtsEntry::class,
        PageDatabase::class, Property::class, PropertyValue::class, PageDatabaseView::class,
        PageRelation::class, PageCanvas::class, CanvasNode::class, CanvasEdge::class,
        PurgedRecord::class, TimeLog::class,
    ],
    // Bump this on ANY change to the entity set or to a column — Room hashes the schema
    // and compares it against the hash stored in `room_master_table` at open time. A hash
    // that moved while `version` stayed put throws IllegalStateException ("you've changed
    // schema but forgot to update the version number") *before* migration runs, so
    // fallbackToDestructiveMigration below never gets the chance to recover: it only
    // handles version changes.
    //
    // v8 is a merge of two independent bumps that each reached a different number from a
    // shared v5: the Canvas tables took it to 6 on one branch, purge tombstones to 7 on
    // the other. The combined entity set hashes to neither, so it has to clear both.
    //
    // v9 adds `uid` to `reminders` and `entry_completions` (S2). Unlike every bump
    // before it, it is migrated rather than destructive — see [MIGRATION_8_9].
    version = 14, // §3.2/§9.9/§5.5.1.1/§9.4 — v5 providerEventId; Canvas tables; purge tombstones; v9 reminder+completion uid; v10 §0.6.4 Entry fields + §0.6.6 habit log; v11 §0.8 step 2b dueDate binding; v12 §0.6.2 Block.mindMap; v13 §0.6.8 schema on a label; v14 §0.6.5 time_logs
    exportSchema = true, // §9.10 — see `shared/schemas/`; a version with no JSON cannot be migrated from
)
@TypeConverters(Converters::class)
@ConstructedBy(TendrilDatabaseConstructor::class)
abstract class TendrilDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun habitDao(): HabitDao
    abstract fun reminderDao(): ReminderDao
    abstract fun entryCompletionDao(): EntryCompletionDao
    abstract fun habitCompletionDao(): HabitCompletionDao
    abstract fun timeLogDao(): TimeLogDao

    abstract fun pageDao(): PageDao
    abstract fun labelDao(): LabelDao
    abstract fun blockDao(): BlockDao
    abstract fun pageFtsDao(): PageFtsDao
    abstract fun pageDatabaseDao(): PageDatabaseDao
    abstract fun propertyDao(): PropertyDao
    abstract fun propertyValueDao(): PropertyValueDao
    abstract fun pageDatabaseViewDao(): PageDatabaseViewDao
    abstract fun pageRelationDao(): PageRelationDao
    abstract fun purgedRecordDao(): PurgedRecordDao
    abstract fun pageCanvasDao(): PageCanvasDao
    abstract fun canvasNodeDao(): CanvasNodeDao
    abstract fun canvasEdgeDao(): CanvasEdgeDao
}

// expect object, actual generated by Room's KSP codegen per target — not hand-written.
expect object TendrilDatabaseConstructor : RoomDatabaseConstructor<TendrilDatabase>

/**
 * §9.10 / S1b — the versions a destructive wipe is still allowed to answer for.
 *
 * These are the pre-release schemas. §9.10's policy is explicitly two-part — "destructive pre-v1,
 * then `@AutoMigration` + manual + snapshot-backed recovery post-v1" — and v9 is where the second
 * half began: the first version with a declared migration, and the first whose data is anyone's
 * real notes rather than something "trivially re-creatable" in §9.10's own words. A database still
 * sitting at v1–v8 belongs to a build that predates all of that, and no migration will ever be
 * written for it.
 *
 * **Narrowed rather than deleted, which is what S1's row asks for.** Deleting the fallback
 * outright would leave a v1–v8 database to [openOrRecover], which would faithfully preserve a file
 * whose contents nothing wants and which nothing can read — a stale copy of a throwaway schema,
 * kept forever. Wiping is the right answer for exactly those versions and the wrong answer for
 * every later one.
 *
 * **What this changes for v9 onward, which is the point of the item.** A version bump with no
 * declared migration, a downgrade from a newer build, and a schema whose hash moved while its
 * version did not — all three used to end in a silent destructive wipe (or, for the last one, a
 * crash §9.10 notes the fallback "never gets the chance to recover" from). All three now reach
 * [openOrRecover] instead, which **renames the database aside rather than destroying it**, starts
 * a fresh one, and says so through `AppContainer.databaseWasRecovered`. That is strictly more
 * recoverable in every case: same end state for the app, plus a copy on disk, plus a person who
 * has been told.
 *
 * It also closes the failure §9.10's acceptance criterion names — "never a silent
 * `fallbackToDestructiveMigration()` left in place after the first release". Forgetting to write a
 * migration for v10 used to erase the database and look like it had worked.
 *
 * **v8 is deliberately not in this list, and Room enforces that it cannot be.** `build()`
 * throws `IllegalArgumentException: Inconsistency detected. A Migration was supplied to
 * addMigration() that has a start or end version equal to a start version supplied to
 * fallbackToDestructiveMigrationFrom()` — because saying both "migrate from 8" and "wipe from
 * 8" is a contradiction. It is also wrong on the merits: v8 has a real declared path to v9
 * ([MIGRATION_8_9]), so it is the one pre-v9 version whose data must never be dropped. Listing
 * it crashed the app on every launch, fresh installs included, and the JVM suite could not see
 * it — nothing there builds a real Room database. Found on a device.
 */
private val PRE_RELEASE_VERSIONS = intArrayOf(1, 2, 3, 4, 5, 6, 7)

/** Shared finish-and-build step; each platform's own `buildTendrilDatabase` supplies the driver. */
internal fun finishBuilding(
    builder: RoomDatabase.Builder<TendrilDatabase>,
    driver: SQLiteDriver,
): TendrilDatabase =
    builder
        .setDriver(driver)
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14) // §9.10 — the declared paths, tried before any fallback
        // §9.10 / S1b — destructive **only** from a pre-v9 schema. See [PRE_RELEASE_VERSIONS].
        .fallbackToDestructiveMigrationFrom(dropAllTables = true, *PRE_RELEASE_VERSIONS)
        .build()
